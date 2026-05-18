package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;
import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

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

    private final DidJwtValidator trustStatementDidJwtValidator;
    private final DidResolverAdapter didResolverAdapter;
    private final UrlRewriteProperties urlRewriteProperties;

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
        log.debug("Trust statement allowlist check passed – DID: {}, URL: {}", didString, didUrl);
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

        var didDoc = didResolverAdapter.resolveDid(didString, urlRewriteProperties.getUrlMappings());
        trustStatementDidJwtValidator.validateJwt(jwtString, didDoc);
        log.debug("Trust statement signature verification succeeded for DID: {}", didString);
    }
}
