package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;
import ch.admin.bj.swiyu.jwtutil.JwtUtilException;
import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.dto.StatusVerificationResultDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListMapper;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListReferenceDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.common.util.time.TimeUtil;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.text.ParseException;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;

/**
 * Validates Trust Statement JWTs (idTS and piaTS) using {@link DidJwtValidator}
 * <ol>
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
    private final CacheProperties cacheProperties;

    private final StatusListCacheService statusListCacheService;
    private final DidResolverFacade keyLoader;
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
            String trustStatementResolutionURL = trustStatementDidJwtValidator.getAndValidateResolutionUrl(jwtString);
            String trustStatementDID = trustStatementDidJwtValidator.getDidString(jwtString);
            log.debug("Trust statement allowlist check passed - DID: {}, URL: {}", trustStatementDID, trustStatementResolutionURL);

            String trustStatementKID = didKidParser.extractKidFromHeader(jwtString);
            SignedJWT trustStatementJWT = SignedJWT.parse(jwtString);
            JWK trustStatementJWK = keyLoader.resolveKey(trustStatementKID);
            trustStatementDidJwtValidator.validateJwt(jwtString, trustStatementJWK);
            log.debug("Trust statement validation passed - DID: {}, URL: {}", trustStatementDID, trustStatementResolutionURL);

            // Extract token status list reference from trust statement
            TokenStatusListReferenceDto statusListReference = TokenStatusListMapper.toTokenStatusListReference(trustStatementJWT.getJWTClaimsSet().getClaims(), trustStatementJWT.getHeader());

            // Get the actual status list with reference info from cache or registry and verify the status list state
            TokenStatusListTokenDto statusList = statusListCacheService.getTokenStatusListTokenByUri(statusListReference.getReferencedStatusListUri());
            StatusVerificationResultDto statusListState = statusListVerifier.verifyStatus(statusListReference, statusList);

            // Check that the status list kid belongs to the trust statement issuer by comparing the DIDs
            var statusListDid = didKidParser.getDidFromAbsoluteKid(statusList.getJwsHeader().getKeyID());
            if (StringUtils.isBlank(statusListDid) || !Objects.equals(statusListDid, trustStatementDID)) {
                log.warn("Status list kid '{}' does not belong to trust statement issuer '{}'", statusList.getJwsHeader().getKeyID(), trustStatementDID);
                return new TrustStatementValidationResult(false, TimeUtil.secondsToNanos(cacheProperties.getRequestBackoffSeconds()));
            }
            
            // Compute TTL in Nanoseconds
            long minimumTimeoutNs = Long.MAX_VALUE;
            minimumTimeoutNs = TimeUtil.minNanosUntilExpiry(minimumTimeoutNs, TimeUtil.secondsToNanos(statusList.getExp()));
            minimumTimeoutNs = TimeUtil.minNanosUntilExpiry(minimumTimeoutNs, trustStatementJWT.getJWTClaimsSet().getExpirationTime());
            // Substract the clock skew from expiration time to ensure that we fetch sufficiently soon the new Trust Statement
            var clockSkewBufferNs = TimeUtil.secondsToNanos(trustRegistryProperties.getClockSkewBufferSeconds());
            minimumTimeoutNs = Math.max(0, minimumTimeoutNs - clockSkewBufferNs);
            minimumTimeoutNs = TimeUtil.minWithNullable(minimumTimeoutNs, TimeUtil.secondsToNanos(statusList.getTtl()));
            minimumTimeoutNs = TimeUtil.minWithNullable(minimumTimeoutNs, TimeUtil.secondsToNanos(trustRegistryProperties.getMaxCacheTtlSeconds()));
            log.debug("Trust statement state validation completed - Validity: {} Cache TTL {} - DID: {}, URL: {}", statusListState.valid(), minimumTimeoutNs, trustStatementDID, trustStatementResolutionURL);

            // If we reached this point the status list state hold the information whether the trust statement can be used. Either way we should not reprocess it until the timeout is through
            return new TrustStatementValidationResult(statusListState.valid(), minimumTimeoutNs);

        } catch (JwtUtilException | IllegalArgumentException | ParseException | IOException e) {
            log.info("Malformed or invalid Trust Statement detected: {} - Ignoring it", jwtString, e);
            return new TrustStatementValidationResult(false, TimeUtil.secondsToNanos(cacheProperties.getRequestBackoffSeconds()));
        }
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
