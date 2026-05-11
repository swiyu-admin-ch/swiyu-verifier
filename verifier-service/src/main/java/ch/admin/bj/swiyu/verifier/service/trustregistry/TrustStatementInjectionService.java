package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.dto.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.dto.requestobject.VerifierInfoEntryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects Trust Protocol 2.0 trust statements into the JWT-Secured Authorization Request.
 * <p>
 * Assembles the {@code verifier_info} array by combining:
 * <ul>
 *   <li>{@code idTS} – Identity Trust Statement (fetched from TMS and cached in-memory)</li>
 *   <li>{@code pvaTS} – Protected Verification Authorization Trust Statement (cached in-memory)</li>
 *   <li>{@code vqPS} – Verification Query Public Statement (persisted per session in the database)</li>
 * </ul>
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
     * Returns a new {@link RequestObjectDto} with the {@code verifier_info} array populated from
     * the active {@code idTS}, {@code pvaTS} and the session-specific {@code vqPS}.
     * <p>
     * Unavailable statements are skipped with a warning; the request object is always returned
     * to ensure graceful degradation.
     *
     * @param requestObject    the base request object to enrich
     * @param managementEntity the current verification session carrying the persisted {@code vqPS}
     * @return a new {@link RequestObjectDto} instance with the {@code verifier_info} array set
     */
    public RequestObjectDto injectVerifierInfo(RequestObjectDto requestObject, Management managementEntity) {
        List<VerifierInfoEntryDto> verifierInfo = new ArrayList<>();

        var override = managementEntity.getConfigurationOverride();
        var clientId = override.verifierDidOrDefault(applicationProperties.getClientId());

        injectIdentityTrustStatement(verifierInfo, clientId);
        injectProtectedVerificationAuthorizationTrustStatement(verifierInfo, clientId);

        // Comes with https://jira.bit.admin.ch/browse/EIDOMNI-869
        // addVqPs(verifierInfo, managementEntity);

        if (verifierInfo.isEmpty()) {
            log.warn("No TP2.0 trust statements available – returning request object without verifier_info");
            return requestObject;
        }

        return requestObject.toBuilder()
                .verifierInfo(verifierInfo)
                .build();
    }

    private void injectProtectedVerificationAuthorizationTrustStatement(List<VerifierInfoEntryDto> verifierInfo, String clientId) {
        String idTs = trustStatementCacheService.getIdentityTrustStatement(clientId);
        if (idTs == null) {
            log.debug("No idTS available for issuer {} – skipping injection", clientId);
            return;
        }
        if (!verifySignatureOrInvalidate(idTs, "idTS", clientId)) {
            return;
        }

        addNullable(verifierInfo, trustStatementCacheService.getProtectedVerificationAuthorizationTrustStatement(clientId), "pvaTS");
    }

    /**
     * Fetches the idTS JWT, verifies its signature, and sets it on the root level
     * of the issuer metadata. On signature failure the cache is invalidated.
     *
     * @param verifierInfo the metadata to update
     * @param issuerDid      the issuer DID to look up in the trust registry
     */
    private void injectIdentityTrustStatement(List<VerifierInfoEntryDto> verifierInfo, String issuerDid) {
        String idTs = trustStatementCacheService.getIdentityTrustStatement(issuerDid);
        if (idTs == null) {
            log.debug("No idTS available for issuer {} – skipping injection", issuerDid);
            return;
        }
        if (!verifySignatureOrInvalidate(idTs, "idTS", issuerDid)) {
            return;
        }
        addNullable(verifierInfo, idTs, "idTS");
    }

    private void addNullable(List<VerifierInfoEntryDto> target, String jwt, String statementType) {
        if (jwt != null) {
            target.add(VerifierInfoEntryDto.ofJwt(jwt));
        } else {
            log.warn("TP2.0: {} not available, skipping injection", statementType);
        }
    }

    /**
     * Verifies the signature of the given trust statement JWT via
     * {@link TrustStatementValidator#validateSignature(String)}.
     * If verification fails, the cache entry for the issuer DID is invalidated
     * so that a fresh statement is fetched on the next request.
     *
     * @param jwt       the trust statement JWT to verify
     * @param type      statement type label for logging ("idTS" or "piaTS")
     * @param issuerDid issuer DID for cache invalidation and logging
     * @return {@code true} if verification succeeded or no validator is configured;
     *         {@code false} if verification failed (cache is invalidated)
     */
    private boolean verifySignatureOrInvalidate(String jwt, String type, String issuerDid) {
        try {
            trustStatementValidator.validateSignature(jwt);
            return true;
        } catch (JwtValidatorException e) {
            log.warn("{} signature verification failed for issuer {} – invalidating cache: {}", type, issuerDid, e.getMessage());
            trustStatementCacheService.invalidateAllTrustStatements(issuerDid);

            return false;
        }
    }
}
