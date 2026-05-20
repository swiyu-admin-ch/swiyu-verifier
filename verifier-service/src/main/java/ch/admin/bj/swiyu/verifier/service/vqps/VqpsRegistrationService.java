package ch.admin.bj.swiyu.verifier.service.vqps;

import ch.admin.bj.swiyu.core.trust.client.api.VqpsSubmissionB2BApi;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsSubmission;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsSubmissionCreateRequest;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsSubmissionStatus;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.vqps.Vqps;
import ch.admin.bj.swiyu.verifier.domain.vqps.VqpsRepository;
import ch.admin.bj.swiyu.verifier.dto.management.VerificationPurposeDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Service responsible for the On-the-Fly vqPS registration flow (EIDOMNI-819).
 *
 * <p>Given a {@link VerificationPurposeDto} and the serialized DCQL query, this service:
 * <ol>
 *   <li>Checks the DB cache for a still-valid vqPS for the given scope.</li>
 *   <li>If absent or expiring before the verification TTL, submits the DCQL query to the
 *       TMS B2B API (IF-014), polling until publication succeeds.</li>
 *   <li>Persists the returned vqPS JWT in the DB for future reuse.</li>
 * </ol>
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0")
public class VqpsRegistrationService {

    /** Language tag keys used to map localized strings to VqpsSubmissionCreateRequest fields. */
    private static final String LANG_EN = "en";
    private static final String LANG_DE_CH = "de-CH";
    private static final String LANG_FR_CH = "fr-CH";
    private static final String LANG_IT_CH = "it-CH";
    private static final String LANG_RM_CH = "rm-CH";

    /** Polling interval when waiting for TMS to publish the vqPS. */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    /** Maximum number of polling attempts before aborting. */
    private static final int MAX_POLL_ATTEMPTS = 10;

    private final TrustRegistryProperties properties;
    private final ApplicationProperties applicationProperties;
    private final VqpsRepository vqpsRepository;
    private final VqpsSubmissionB2BApi vqpsSubmissionB2BApi;
    private final ObjectMapper objectMapper;

    /**
     * Ensures a valid vqPS exists for the given scope and DCQL query and returns its query hash.
     *
     * <p>The returned hash is the primary key of the {@code vqps_cache} table. It is stored on
     * the {@link ch.admin.bj.swiyu.verifier.domain.management.Management} entity so that the
     * request object service can look up the correct vqPS JWT at injection time via a direct PK
     * lookup – without any scope ambiguity.</p>
     *
     * <p>Checks the DB cache first. If the cached entry is still valid after the given
     * verification TTL (plus buffer), the existing hash is returned immediately. Otherwise a new
     * vqPS is submitted to the TMS B2B API and the result is persisted.</p>
     *
     * @param purpose               the transparency metadata including scope and localized strings
     * @param dcqlQueryJson         the serialized DCQL query object
     * @param verificationExpiresAt the Unix epoch second at which the verification session expires
     * @return the SHA-256 query hash (PK of {@code vqps_cache}) identifying the valid cache entry
     * @throws IllegalStateException if the newly fetched vqPS expires before the verification TTL,
     *                               or if the TMS submission fails or times out
     */
    public String getOrRegisterVqps(VerificationPurposeDto purpose, Object dcqlQueryJson, long verificationExpiresAt) {
        String scope = purpose.scope();
        long requiredValidUntil = verificationExpiresAt + properties.getVqpsExpiryBufferSeconds();
        String currentHash = computeQueryHash(purpose, dcqlQueryJson);

        Optional<Vqps> cached = vqpsRepository.findById(currentHash);
        if (cached.isPresent() && cached.get().getExpiresAt() > requiredValidUntil) {
            log.debug("vqPS cache hit for scope={}, hash={}", scope, currentHash);
            return currentHash;
        }

        log.info("No valid vqPS cache entry found for scope={}, submitting to TMS B2B API", scope);
        String jwt = submitAndPollForJwt(purpose, dcqlQueryJson);
        long expiry = extractExpSeconds(jwt);

        if (expiry <= requiredValidUntil) {
            throw new IllegalStateException(
                    "Newly obtained vqPS for scope '" + scope + "' expires at " + expiry
                            + " which is before the verification TTL " + requiredValidUntil
                            + ". Cannot proceed.");
        }

        persistVqps(currentHash, scope, jwt, expiry);
        return currentHash;
    }

    /**
     * Persists the vqPS cache entry in its own transaction, isolated from the reactive polling.
     *
     * @param queryHash the SHA-256 hash (PK)
     * @param scope     the scope identifier for logging
     * @param jwt       the compact-serialized vqPS JWT
     * @param expiresAt the expiry in Unix epoch seconds
     */
    @Transactional
    protected void persistVqps(String queryHash, String scope, String jwt, long expiresAt) {
        Vqps entry = Vqps.builder()
                .queryHash(queryHash)
                .scope(scope)
                .jwt(jwt)
                .expiresAt(expiresAt)
                .build();
        vqpsRepository.save(entry);
        log.info("vqPS stored in DB for scope={}, expires_at={}", scope, expiresAt);
    }

    /**
     * Submits the vqPS to the TMS B2B API and polls until the publication succeeds.
     *
     * @param purpose       the transparency metadata
     * @param dcqlQueryJson the serialized DCQL query
     * @return the signed vqPS JWT from the publication result
     * @throws IllegalStateException if publication fails or polling times out
     */
    private String submitAndPollForJwt(VerificationPurposeDto purpose, Object dcqlQueryJson) {
        VqpsSubmissionCreateRequest request = buildSubmissionRequest(purpose, dcqlQueryJson);

        return vqpsSubmissionB2BApi.createVqpsSubmission(request)
                .flatMap(submission -> {
                    if (submission == null) {
                        return Mono.error(new IllegalStateException(
                                "TMS B2B API returned null response for vqPS submission, scope=" + purpose.scope()));
                    }
                    log.debug("vqPS submission created with id={}, status={}", submission.getId(), submission.getStatus());
                    if (submission.getStatus() == VqpsSubmissionStatus.PUBLICATION_SUCCEEDED) {
                        return Mono.just(extractJwtFromSubmission(submission, purpose.scope()));
                    }
                    return pollUntilPublished(submission.getId(), purpose.scope());
                })
                .block();
    }

    /**
     * Polls the TMS B2B API reactively until the submission reaches a terminal status.
     *
     * <p>Uses Reactor's {@link Retry#fixedDelay} so no thread is blocked during the
     * wait interval. Retries are triggered only for {@link PollingPendingException};
     * terminal failures propagate immediately without further retries.</p>
     *
     * @param submissionId the UUID of the submission to poll
     * @param scope        scope identifier for logging and error messages
     * @return a {@link Mono} emitting the vqPS JWT once published
     */
    private Mono<String> pollUntilPublished(UUID submissionId, String scope) {
        return Mono.defer(() -> vqpsSubmissionB2BApi.getVqpsSubmission(submissionId))
                .flatMap(submission -> {
                    if (submission == null) {
                        return Mono.error(new IllegalStateException(
                                "TMS B2B API returned null when polling submission id=" + submissionId));
                    }
                    log.debug("Polling vqPS submission id={}, status={}", submissionId, submission.getStatus());
                    return switch (submission.getStatus()) {
                        case PUBLICATION_SUCCEEDED -> Mono.just(extractJwtFromSubmission(submission, scope));
                        case PUBLCATION_FAILED -> Mono.error(new IllegalStateException(
                                "TMS vqPS publication failed for scope='" + scope + "', reason=" + submission.getFailureReason()));
                        default -> Mono.error(new PollingPendingException(
                                "vqPS submission " + submissionId + " still pending"));
                    };
                })
                .retryWhen(Retry.fixedDelay(MAX_POLL_ATTEMPTS, POLL_INTERVAL)
                        .filter(t -> t instanceof PollingPendingException)
                        .onRetryExhaustedThrow((spec, signal) -> new IllegalStateException(
                                "Timed out waiting for vqPS publication for scope='" + scope
                                        + "' after " + MAX_POLL_ATTEMPTS + " attempts"))
                );
    }

    /**
     * Extracts the JWT string from a successfully published {@link VqpsSubmission}.
     *
     * @param submission the submission in status {@code PUBLICATION_SUCCEEDED}
     * @param scope      scope identifier for error messages
     * @return the compact-serialized vqPS JWT
     * @throws IllegalStateException if {@code publicationResult} or its JWT is absent
     */
    private String extractJwtFromSubmission(VqpsSubmission submission, String scope) {
        var result = submission.getPublicationResult();
        if (result == null || result.getJwt() == null) {
            throw new IllegalStateException(
                    "TMS vqPS submission succeeded but publicationResult.jwt is missing, scope=" + scope);
        }
        return result.getJwt();
    }

    /**
     * Builds a {@link VqpsSubmissionCreateRequest} from the given purpose and DCQL query.
     *
     * <p>Maps the localized {@code purposeName} and {@code purposeDescription} maps
     * to the language-specific fields of the request. The mandatory {@code purpose_name}
     * and {@code purpose_description} fields are populated with the first available value
     * as a fallback.</p>
     *
     * @param purpose       the transparency metadata
     * @param dcqlQueryJson the serialized DCQL query
     * @return a fully populated {@link VqpsSubmissionCreateRequest}
     */
    private VqpsSubmissionCreateRequest buildSubmissionRequest(VerificationPurposeDto purpose, Object dcqlQueryJson) {
        Map<String, String> names = purpose.purposeName();
        Map<String, String> descs = purpose.purposeDescription();

        String defaultName = names.values().stream().findFirst().orElseThrow(
                () -> new IllegalArgumentException("purposeName map must not be empty"));
        String defaultDesc = descs.values().stream().findFirst().orElseThrow(
                () -> new IllegalArgumentException("purposeDescription map must not be empty"));

        return new VqpsSubmissionCreateRequest()
                .sub(applicationProperties.getClientId())
                .scope(purpose.scope())
                .purposeName(defaultName)
                .purposeNameHashEn(names.get(LANG_EN))
                .purposeNameHashDeCH(names.get(LANG_DE_CH))
                .purposeNameHashFrCH(names.get(LANG_FR_CH))
                .purposeNameHashItCH(names.get(LANG_IT_CH))
                .purposeNameHashRmCH(names.get(LANG_RM_CH))
                .purposeDescription(defaultDesc)
                .purposeDescriptionHashEn(descs.get(LANG_EN))
                .purposeDescriptionHashDeCH(descs.get(LANG_DE_CH))
                .purposeDescriptionHashFrCH(descs.get(LANG_FR_CH))
                .purposeDescriptionHashItCH(descs.get(LANG_IT_CH))
                .purposeDescriptionHashRmCH(descs.get(LANG_RM_CH))
                .query(dcqlQueryJson);
    }

    /**
     * Computes a SHA-256 hex digest over the canonical combination of DCQL query,
     * purpose name map and purpose description map.
     *
     * <p>The input is serialized to a stable JSON string (sorted keys via
     * {@link ObjectMapper}) so that semantically identical inputs always produce
     * the same hash regardless of map iteration order.</p>
     *
     * <p>If {@code dcqlQueryJson} cannot be serialized, the method falls back to
     * {@link Object#toString()} to avoid a hard failure at cache-check time.</p>
     *
     * @param purpose       the transparency metadata
     * @param dcqlQueryJson the DCQL query object
     * @return lowercase hex SHA-256 digest of the canonical input
     */
    private String computeQueryHash(VerificationPurposeDto purpose, Object dcqlQueryJson) {
        try {
            // Use a TreeMap-sorted serialization to ensure key order is stable
            String canonical = objectMapper.writeValueAsString(Map.of(
                    "dcql", dcqlQueryJson,
                    "purpose_name", new java.util.TreeMap<>(purpose.purposeName()),
                    "purpose_description", new java.util.TreeMap<>(purpose.purposeDescription())
            ));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute vqPS query hash for scope=" + purpose.scope(), e);
        }
    }

    /**
     * Parses the JWT without signature verification and extracts the {@code exp} claim
     * in Unix epoch seconds.
     *
     * @param jwt the compact-serialized JWT
     * @return the expiry in epoch seconds
     * @throws IllegalStateException if parsing fails
     */
    private long extractExpSeconds(String jwt) {
        try {
            var expDate = JWTParser.parse(jwt).getJWTClaimsSet().getExpirationTime();
            if (expDate == null) {
                throw new IllegalStateException("vqPS JWT has no exp claim");
            }
            return expDate.getTime() / 1000;
        } catch (ParseException e) {
            throw new IllegalStateException("Failed to parse vqPS JWT exp claim", e);
        }
    }

    /**
     * Sentinel exception used exclusively to signal a pending TMS submission status
     * to the Reactor retry mechanism. Never propagated to callers.
     */
    private static class PollingPendingException extends RuntimeException {
        PollingPendingException(String message) {
            super(message, null, true, false); // suppress stacktrace – purely a control-flow signal
        }
    }

}
