package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.vqps.VqpsRepository;
import ch.admin.bj.swiyu.verifier.dto.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.dto.requestobject.VerifierInfoEntryDto;
import com.nimbusds.jwt.JWTParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Injects Trust Protocol 2.0 trust statements into the JWT-Secured Authorization Request.
 * <p>
 * Assembles the {@code verifier_info} array by combining:
 * <ul>
 *   <li>{@code idTS} – Identity Trust Statement (fetched from TMS and cached in-memory)</li>
 *   <li>{@code pvaTS} – Protected Verification Authorization Trust Statements (one per
 *       {@code authorized_fields} scope; cached in-memory as a list)</li>
 *   <li>{@code vqPS} – Verification Query Public Statement (persisted per session in the database)</li>
 * </ul>
 * <p>
 * The {@code pvaTS} selection is demand-driven: only those pvaTS JWTs whose
 * {@code authorized_fields} array covers at least one claim path requested in the current DCQL
 * query are selected and injected. This avoids bloating the request object with irrelevant statements.
 * <p>
 * Thread safety is guaranteed by the builder pattern: a fresh {@link RequestObjectDto} is created
 * per call via {@code toBuilder().build()} – no shared mutable state is mutated.
 * <p>
 * Conditionally active: if {@code swiyu.trust-registry.api-url} is absent, the Authorization
 * Request is returned unmodified and no trust statements are injected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(TrustStatementCacheService.class)
public class TrustStatementInjectionService {

    /**
     * JWT claim name for the list of authorized fields within a pvaTS.
     */
    private static final String AUTHORIZED_FIELDS_CLAIM = "authorized_fields";

    private final TrustStatementCacheService trustStatementCacheService;
    private final ApplicationProperties applicationProperties;

    /**
     * Validator for signature verification at inject time.
     * When present, each cached trust statement JWT is verified against the
     * Trust Registry's current DID Document before injection. This ensures
     * key rotations are detected immediately, without waiting for cache expiry.
     * On failure the cache entry is invalidated so a fresh statement is fetched next time.
     */
    private final TrustStatementValidator trustStatementValidator;

    /**
     * Optional repository for persisted vqPS cache entries.
     * When present and the management entity carries a vqPS scope, the corresponding
     * vqPS JWT is injected into the {@code verifier_info} array.
     */
    private final Optional<VqpsRepository> vqpsCacheRepository;

    /**
     * Returns a new {@link RequestObjectDto} with the {@code verifier_info} array populated from
     * the active {@code idTS}, the matching {@code pvaTS} JWTs and the session-specific {@code vqPS}.
     * <p>
     * The {@code pvaTS} JWTs are filtered to include only those whose {@code authorized_fields}
     * claim covers at least one field path requested in the DCQL query of the given session.
     * Unavailable or failed statements are skipped with a warning; the request object is always
     * returned to ensure graceful degradation.
     *
     * @param requestObject    the base request object to enrich
     * @param managementEntity the current verification session carrying the DCQL query and
     *                         persisted {@code vqPS}
     * @return a new {@link RequestObjectDto} instance with the {@code verifier_info} array set
     */
    public RequestObjectDto injectVerifierInfo(RequestObjectDto requestObject, Management managementEntity) {
        List<VerifierInfoEntryDto> verifierInfo = new ArrayList<>();

        var override = managementEntity.getConfigurationOverride();
        var clientId = override.verifierDidOrDefault(applicationProperties.getClientId());

        injectIdentityTrustStatement(verifierInfo, clientId);
        injectProtectedVerificationAuthorizationTrustStatements(verifierInfo, clientId, managementEntity);
        injectVqPs(verifierInfo, managementEntity);

        if (verifierInfo.isEmpty()) {
            log.warn("No TP2.0 trust statements available – returning request object without verifier_info");
            return requestObject;
        }

        return requestObject.toBuilder()
                .verifierInfo(verifierInfo)
                .build();
    }

    /**
     * Fetches the idTS JWT, verifies its signature, and adds it to the verifier_info list.
     * On signature failure the cache is invalidated.
     *
     * @param verifierInfo the list to append to
     * @param issuerDid    the verifier DID to look up in the trust registry
     */
    private void injectIdentityTrustStatement(List<VerifierInfoEntryDto> verifierInfo, String issuerDid) {
        String idTs = trustStatementCacheService.getIdentityTrustStatement(issuerDid);
        if (idTs == null) {
            log.debug("No idTS available for DID {} – skipping injection", issuerDid);
            return;
        }
        if (!verifySignatureOrInvalidate(idTs, "idTS", issuerDid)) {
            return;
        }
        verifierInfo.add(VerifierInfoEntryDto.ofJwt(idTs));
    }

    /**
     * Fetches all cached {@code pvaTS} JWTs, selects those covering at least one requested
     * field from the session's DCQL query, verifies each selected JWT's signature, and
     * appends them to the {@code verifier_info} list.
     *
     * @param verifierInfo     the list to append to
     * @param verifierDid      the verifier DID whose pvaTS statements are cached
     * @param managementEntity the current verification session providing the DCQL query
     */
    private void injectProtectedVerificationAuthorizationTrustStatements(
            List<VerifierInfoEntryDto> verifierInfo, String verifierDid, Management managementEntity) {

        List<String> allPvaTs = trustStatementCacheService.getProtectedVerificationAuthorizationTrustStatements(verifierDid);
        if (allPvaTs.isEmpty()) {
            log.debug("No pvaTS available for verifier {} – skipping injection", verifierDid);
            return;
        }

        Set<String> requestedFields = extractRequestedFieldPaths(managementEntity);
        log.debug("Selecting pvaTS for verifier {} based on {} requested field path(s)", verifierDid, requestedFields.size());

        List<String> selected = allPvaTs.stream()
                .filter(jwt -> pvaTsCoversRequestedFields(jwt, requestedFields))
                .toList();

        if (selected.isEmpty()) {
            log.debug("No pvaTS JWT covers the requested fields for verifier {} – skipping injection", verifierDid);
            return;
        }

        for (String pvaTsJwt : selected) {
            if (verifySignatureOrInvalidate(pvaTsJwt, "pvaTS", verifierDid)) {
                verifierInfo.add(VerifierInfoEntryDto.ofJwt(pvaTsJwt));
            }
        }
    }

    /**
     * Looks up the persisted vqPS JWT for the scope stored on the management entity and,
     * if found and still valid, appends it to the {@code verifier_info} list.
     *
     * <p>Per Trust Protocol 2.0, the vqPS is embedded as
     * {@code {"format": "jwt", "data": "<jwt>"}}. The scope on the Authorization Request
     * must match the scope used during registration.</p>
     *
     * <p><b>Defense-in-depth:</b> the vqPS JWT is re-validated on every injection
     * (signature against the configured TMS issuer DID, plus {@code exp} freshness).
     * This means a tampered or expired DB row can never reach the signed Authorization
     * Request, even though the row was originally written by the verifier itself.</p>
     *
     * @param verifierInfo     the list to append to
     * @param managementEntity the current verification session
     */
    private void injectVqPs(List<VerifierInfoEntryDto> verifierInfo, Management managementEntity) {
        String queryHash = managementEntity.getVqpsQueryHash();
        if (queryHash == null) {
            return;
        }
        if (vqpsCacheRepository.isEmpty()) {
            log.debug("VqpsCacheRepository not available – skipping vqPS injection for hash={}", queryHash);
            return;
        }
        vqpsCacheRepository.get().findById(queryHash).ifPresentOrElse(
                entry -> {
                    if (isVqPsValid(entry.getJwt(), queryHash)) {
                        verifierInfo.add(VerifierInfoEntryDto.ofJwt(entry.getJwt()));
                    }
                },
                () -> log.warn("No vqPS found in DB for hash={} – skipping injection", queryHash)
        );
    }

    /**
     * Validates a vqPS JWT before injection: signature against the configured TMS issuer DID
     * (via the same {@link TrustStatementValidator} used for idTS/pvaTS) plus {@code exp}
     * freshness against the current system clock.
     *
     * @param jwt       the compact-serialized vqPS JWT
     * @param queryHash the cache PK – used in log messages
     * @return {@code true} if the JWT is signature-valid and not expired; {@code false} otherwise
     */
    private boolean isVqPsValid(String jwt, String queryHash) {
        try {
            var expDate = JWTParser.parse(jwt).getJWTClaimsSet().getExpirationTime();
            if (expDate == null || expDate.toInstant().isBefore(java.time.Instant.now())) {
                log.warn("vqPS JWT for hash={} is missing or past exp – skipping injection", queryHash);
                return false;
            }
        } catch (ParseException e) {
            log.warn("vqPS JWT for hash={} is unparseable – skipping injection: {}", queryHash, e.getMessage());
            return false;
        }
        try {
            trustStatementValidator.validateSignature(jwt);
            return true;
        } catch (JwtValidatorException e) {
            log.warn("vqPS signature verification failed for hash={} – skipping injection: {}", queryHash, e.getMessage());
            return false;
        }
    }

    /**
     * Extracts the flat set of claim path leaf values (field names) from all DCQL credential
     * queries in the session. These are used to match against the {@code authorized_fields}
     * arrays of the cached pvaTS JWTs.
     *
     * <p>Only the last element of each path array is used as the field identifier, consistent
     * with how TP 2.0 specifies {@code authorized_fields} as a list of claim names.</p>
     *
     * @param managementEntity the current session
     * @return a set of field name strings; never {@code null}
     */
    private Set<String> extractRequestedFieldPaths(Management managementEntity) {
        var dcqlQuery = managementEntity.getDcqlQuery();
        if (dcqlQuery == null || dcqlQuery.getCredentials() == null) {
            return Set.of();
        }
        return dcqlQuery.getCredentials().stream()
                .map(DcqlCredential::getClaims)
                .filter(claims -> claims != null)
                .flatMap(Collection::stream)
                .map(DcqlClaim::getPath)
                .filter(path -> path != null && !path.isEmpty())
                .map(path -> String.valueOf(path.getLast()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns {@code true} if the given pvaTS JWT's {@code authorized_fields} claim contains
     * at least one field from the requested set. Parses the JWT payload without signature
     * verification (the signature check happens separately via {@link #verifySignatureOrInvalidate}).
     *
     * @param pvaTsJwt        the pvaTS JWT string
     * @param requestedFields the set of field names requested by the DCQL query
     * @return {@code true} if there is any overlap between authorized and requested fields
     */
    private boolean pvaTsCoversRequestedFields(String pvaTsJwt, Set<String> requestedFields) {
        if (requestedFields.isEmpty()) {
            return true;
        }
        try {
            var claims = JWTParser.parse(pvaTsJwt).getJWTClaimsSet();
            var authorizedFields = claims.getStringListClaim(AUTHORIZED_FIELDS_CLAIM);
            if (authorizedFields == null || authorizedFields.isEmpty()) {
                log.debug("pvaTS has no {} claim – including unconditionally", AUTHORIZED_FIELDS_CLAIM);
                return true;
            }
            boolean matches = authorizedFields.stream().anyMatch(requestedFields::contains);
            log.debug("pvaTS authorized_fields={} covers requested={}: {}", authorizedFields, requestedFields, matches);
            return matches;
        } catch (ParseException e) {
            log.warn("Failed to parse pvaTS JWT payload for authorized_fields extraction: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the signature of the given trust statement JWT via
     * {@link TrustStatementValidator#validateSignature(String)}.
     * If verification fails, the cache entry for the DID is invalidated
     * so that a fresh statement is fetched on the next request.
     *
     * @param jwt    the trust statement JWT to verify
     * @param type   statement type label for logging ("idTS" or "pvaTS")
     * @param did    DID for cache invalidation and logging
     * @return {@code true} if verification succeeded; {@code false} if it failed
     */
    private boolean verifySignatureOrInvalidate(String jwt, String type, String did) {
        try {
            trustStatementValidator.validateSignature(jwt);
            return true;
        } catch (JwtValidatorException e) {
            log.warn("{} signature verification failed for DID {} – invalidating cache: {}", type, did, e.getMessage());
            trustStatementCacheService.invalidateAllTrustStatements(did);
            return false;
        }
    }
}
