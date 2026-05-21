package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
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
 *   <li>{@code pvaTS} – Protected Verification Authorization Trust Statements (cached in-memory as a list)</li>
 * </ul>
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

    private final TrustStatementCacheService trustStatementCacheService;

    /**
     * Validator for signature verification at inject time.
     * Each cached trust statement JWT is verified against the Trust Registry's current
     * DID Document before injection. This ensures key rotations are detected immediately,
     * without waiting for cache expiry. On failure the cache entry is invalidated so a
     * fresh statement is fetched next time.
     */
    private final TrustStatementValidator trustStatementValidator;

    /**
     * Returns a new {@link RequestObjectDto} with the {@code verifier_info} array populated from
     * the active {@code idTS} and all available {@code pvaTS} JWTs that pass signature verification.
     * Unavailable or failed statements are skipped with a warning; the request object is always
     * returned to ensure graceful degradation.
     *
     * @param requestObject the base request object to enrich
     * @param verifierDid   the verifier DID under which idTS / pvaTS are looked up in the
     *                      trust-registry cache (already resolved by the caller, taking any
     *                      per-management override into account)
     * @return a new {@link RequestObjectDto} instance with the {@code verifier_info} array set
     */
    public RequestObjectDto injectVerifierInfo(RequestObjectDto requestObject, String verifierDid) {
        List<VerifierInfoEntryDto> verifierInfo = new ArrayList<>();

        injectIdentityTrustStatement(verifierInfo, verifierDid);
        injectProtectedVerificationAuthorizationTrustStatements(verifierInfo, verifierDid);

        if (verifierInfo.isEmpty()) {
            log.warn("No TP2.0 trust statements available – returning request object without verifier_info");
            return requestObject;
        }

        log.debug("Injecting TP2.0 verifier_info for verifier {}: {} statement(s)", verifierDid, verifierInfo.size());
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
     * Fetches all cached {@code pvaTS} JWTs and appends every entry that passes signature
     * verification to {@code verifier_info}.
     *
     * <p>No filtering against the session's DCQL query is performed: the pvaTS authorizes
     * the verifier as a whole; wallets compare requested fields against the pvaTS
     * {@code authorized_fields} on their side. Injecting all valid pvaTS does not grant the
     * verifier any extra authority.</p>
     *
     * @param verifierInfo the list to append to
     * @param verifierDid  the verifier DID whose pvaTS statements are cached
     */
    private void injectProtectedVerificationAuthorizationTrustStatements(
            List<VerifierInfoEntryDto> verifierInfo, String verifierDid) {

        List<String> allPvaTs = trustStatementCacheService.getProtectedVerificationAuthorizationTrustStatements(verifierDid);
        if (allPvaTs.isEmpty()) {
            log.debug("No pvaTS available for verifier {} – skipping injection", verifierDid);
            return;
        }

        for (String pvaTsJwt : allPvaTs) {
            if (verifySignatureOrInvalidate(pvaTsJwt, "pvaTS", verifierDid)) {
                verifierInfo.add(VerifierInfoEntryDto.ofJwt(pvaTsJwt));
            }
        }
    }

    /**
     * Verifies the signature of the given trust statement JWT via
     * {@link TrustStatementValidator#validateSignature(String)}.
     * If verification fails, the cache entry for the DID is invalidated
     * so that a fresh statement is fetched on the next request.
     *
     * @param jwt  the trust statement JWT to verify
     * @param type statement type label for logging ("idTS" or "pvaTS")
     * @param did  DID for cache invalidation and logging
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
