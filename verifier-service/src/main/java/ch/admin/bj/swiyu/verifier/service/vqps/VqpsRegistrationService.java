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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for the On-the-Fly vqPS registration flow (EIDOMNI-819).
 *
 * <p>Given a {@link VerificationPurposeDto} and the serialized DCQL query, this service:
 * <ol>
 *   <li>Checks the DB cache for a still-valid vqPS for the given scope.</li>
 *   <li>If absent or expiring before the verification TTL, submits the DCQL query to the
 *       TMS B2B API (IF-014) with {@code waitForPublication=true}, which blocks until the
 *       TMS has published the vqPS or returns an error – no client-side polling needed.</li>
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
        String jwt = submitAndAwaitJwt(purpose, dcqlQueryJson);
        long expiry = extractExpSeconds(jwt);

        if (expiry <= requiredValidUntil) {
            throw new IllegalStateException(
                    "Newly obtained vqPS for scope '" + scope + "' expires at " + expiry
                            + " which is before the verification TTL " + requiredValidUntil
                            + ". Cannot proceed.");
        }

        vqpsRepository.save(Vqps.builder()
                .queryHash(currentHash)
                .scope(scope)
                .jwt(jwt)
                .expiresAt(expiry)
                .build());
        return currentHash;
    }

    /**
     * Submits the vqPS to the TMS B2B API with {@code waitForPublication=true} and returns the
     * published vqPS JWT synchronously.
     *
     * <p>The TMS API blocks the response until publication succeeds or fails, so no
     * client-side polling is required.</p>
     *
     * @param purpose       the transparency metadata
     * @param dcqlQueryJson the serialized DCQL query
     * @return the signed vqPS JWT from the publication result
     * @throws IllegalStateException if publication fails or the response is invalid
     */
    private String submitAndAwaitJwt(VerificationPurposeDto purpose, Object dcqlQueryJson) {
        VqpsSubmissionCreateRequest request = buildSubmissionRequest(purpose, dcqlQueryJson);

        VqpsSubmission submission = vqpsSubmissionB2BApi.createVqpsSubmission(request).block();
        if (submission == null) {
            throw new IllegalStateException(
                    "TMS B2B API returned null response for vqPS submission, scope=" + purpose.scope());
        }
        log.debug("vqPS submission returned with id={}, status={}", submission.getId(), submission.getStatus());

        if (submission.getStatus() == VqpsSubmissionStatus.PUBLICATION_FAILED) {
            throw new IllegalStateException(
                    "TMS vqPS publication failed for scope='" + purpose.scope()
                            + "', reason=" + submission.getPublicationFailureReason());
        }
        if (submission.getStatus() != VqpsSubmissionStatus.PUBLICATION_SUCCEEDED) {
            throw new IllegalStateException(
                    "TMS vqPS submission returned unexpected status=" + submission.getStatus()
                            + " for scope='" + purpose.scope() + "'");
        }
        return extractJwtFromSubmission(submission, purpose.scope());
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
        return new VqpsSubmissionCreateRequest()
                .waitForPublication(true)
                .sub(applicationProperties.getClientId())
                .scope(purpose.scope())
                .purposeName(purpose.purposeName())
                .purposeDescription(purpose.purposeDescription())
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


}
