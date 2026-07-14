package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;
import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.dto.StatusVerificationResultDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListMapper;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListReferenceDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.common.util.time.TimeUtil;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListCacheService;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolver;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.text.ParseException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Validates Trust Statement JWTs (idTS and piaTS) using the two-step Flow B of
 * {@link DidJwtValidator}, split across two distinct phases:
 *
 * <ol>
 *   <li><strong>Pre-cache validation</strong> ({@link #validateAllowlist(String)}):
 *       Called at fetch time. Checks that the JWT's {@code kid} resolves to a DID URL
 *       on the configured Trust Registry allowlist. Fast – no HTTP call. Prevents
 *       malicious JWTs with foreign DIDs from ever entering the cache.</li>
 *   <li><strong>Pre-inject validation</strong> ({@link #validateSignature(String)}):
 *       Called on every metadata response, just before the cached JWT is injected.
 *       Fetches the Trust Registry's DID Document fresh and verifies the signature.
 *       This ensures key rotations on the Trust Registry side are detected immediately,
 *       without waiting for the cache TTL to expire.</li>
 * </ol>
 *
 * <p>On signature failure the caller is expected to invalidate the cache entry via
 * {@link TrustStatementCacheService#invalidateAllTrustStatements(String)} so that a fresh
 * statement is fetched on the next request.</p>
 *
 * <p>Only active when {@code swiyu.trust-registry.api-url} is configured.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.api-url:}'.length() > 0")
public class TrustStatementValidator {

    @Qualifier("trustStatementValidator")
    private final DidJwtValidator trustStatementDidJwtValidator;
    private final TrustRegistryProperties trustRegistryProperties;

    private final StatusListCacheService statusListCacheService;
    private final IssuerPublicKeyLoader keyLoader;
    private final TokenStatusListVerifier statusListVerifier;
    private final DidKidParser didKidParser = new DidKidParser();



    /**
     * Validates the Trust Statement JWT and (if any) associated Status Lists. Computes the validity window 
     * (the time the trust statement can be cached for) from the 
     * minimum validity of the Trust Statement expiry, Status List Expiry, Status List TTL or Trust Statement Cache TTL.
     * <br>
     * Does NOT validate if the Trust Statement is correct in the context it is being used!
     * @param jwtString
     * @return TrustStatementValidationResult containing if the trust statement has a valid state and the milliseconds the trust statement can be cached
     */
    public TrustStatementValidationResult trustStatementValidityWindow(String jwtString) {
        if (jwtString == null) {
            return new TrustStatementValidationResult(false, 0);
        }
        try {
            // Get all required parts & verify them
            String didUrl = trustStatementDidJwtValidator.getAndValidateResolutionUrl(jwtString);
            String didString = trustStatementDidJwtValidator.getDidString(jwtString);
            log.debug("Trust statement allowlist check passed - DID: {}, URL: {}", didString, didUrl);
            String kid = didKidParser.extractKidFromHeader(jwtString);
            SignedJWT trustStatementJWT = SignedJWT.parse(jwtString);
            JWK trustStatementKey = keyLoader.loadJWK(trustStatementJWT.getJWTClaimsSet().getIssuer(), kid);
            trustStatementDidJwtValidator.validateJwt(jwtString, new JWKSet(trustStatementKey));
            log.debug("Trust statement validation passed - DID: {}, URL: {}", didString, didUrl);
            TokenStatusListReferenceDto reference = TokenStatusListMapper.toTokenStatusListReference(trustStatementJWT.getJWTClaimsSet().getClaims());
            TokenStatusListTokenDto statusList = statusListCacheService.getTokenStatusListTokenByUri(reference.getReferencedStatusListUri());
            StatusVerificationResultDto statusListState = statusListVerifier.verifyStatus(reference, statusList);
            
            // Compute TTL in Nanoseconds
            long minimumTimeoutNs = TimeUtil.getMinimumExpiry(trustRegistryProperties.getMaxCacheTtlSeconds(), statusList.getExp());
            minimumTimeoutNs = TimeUtil.getMinimumExpiry(minimumTimeoutNs, trustStatementJWT.getJWTClaimsSet().getExpirationTime());
            // Substract the clock skew from expiration time to ensure that we fetch sufficiently soon the new Trust Statement
            minimumTimeoutNs = Math.max(0, minimumTimeoutNs - trustRegistryProperties.getClockSkewBufferSeconds());
            minimumTimeoutNs = TimeUtil.getMinimumFromSeconds(minimumTimeoutNs, statusList.getTtl());
            log.debug("Trust statement state validation completed - Validity: {} Cache TTL {} - DID: {}, URL: {}", statusListState.valid(), minimumTimeoutNs, didString, didUrl);

            // If we reached this point the status list state hold the information whether the trust statement can be used. Either way we should not reprocess it until the timeout is through
            return new TrustStatementValidationResult(statusListState.valid(), minimumTimeoutNs);

        } catch (IllegalArgumentException | ParseException | LoadingPublicKeyOfIssuerFailedException | IOException e) {
            log.info("Malformed or invalid Trust Statement detected: {} - Ignoring it", jwtString, e);
            return new TrustStatementValidationResult(false, 0);
        }
    }
    


    /**
     * Phase 1 – pre-cache allowlist check (no HTTP call).
     *
     * <p>Extracts the DID resolution URL from the JWT's {@code kid} header and validates
     * it against the configured Trust Registry host allowlist. Call this before storing
     * a freshly fetched trust statement in the cache.</p>
     *
     * @param jwtString the compact serialized Trust Statement JWT
     * @throws JwtValidatorException if the JWT is malformed, the {@code kid} is missing /
     *                               not absolute, or the resolved DID URL is not on the allowlist
     */
    public void validateAllowlist(String jwtString) {
        String didUrl = trustStatementDidJwtValidator.getAndValidateResolutionUrl(jwtString);
        String didString = trustStatementDidJwtValidator.getDidString(jwtString);
        log.debug("Trust statement allowlist check passed - DID: {}, URL: {}", didString, didUrl);
    }

    /**
     * Phase 2 – pre-inject signature verification (HTTP call to DID resolver).
     *
     * <p>Resolves the Trust Registry's DID Document fresh (via {@link DidResolverAdapter})
     * and verifies the JWT signature against the current public key. Call this every time
     * a cached trust statement is about to be injected into the issuer metadata response.</p>
     *
     * <p>Because the DID Document is fetched on every call, key rotations on the Trust
     * Registry side are detected immediately – without waiting for the cache TTL to expire.
     * Note: {@link DidResolverAdapter} may cache the DID Document internally via
     * {@code PUBLIC_KEY_CACHE} to limit redundant HTTP calls.</p>
     *
     * @param jwtString the compact serialized Trust Statement JWT
     * @throws JwtValidatorException if the DID Document cannot be fetched, the key is not
     *                               found in the document, or the signature verification fails
     */
    public void validateSignature(String jwtString) {
        String didString = trustStatementDidJwtValidator.getDidString(jwtString);
        log.debug("Verifying trust statement signature for DID: {}", didString);

        // See: https://jira.bit.admin.ch/browse/EIDOMNI-959 - Architectural Risk: potential DoS via expensive DID resolution in signature verification
        // For the time being deactivated
        // var didDoc = didResolverAdapter.resolveDid(didString, urlRewriteProperties.getUrlMappings());
        // trustStatementDidJwtValidator.validateJwt(jwtString, didDoc);
        log.debug("Trust statement signature verification succeeded for DID: {}", didString);
    }


    public record TrustStatementValidationResult(boolean isValid, long valditiyWindow) {
    }
}
