package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;
import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Validates Trust Protocol 2.0 trust statement JWTs ({@code idTS} and {@code pvaTS}) using the
 * two-step Flow B of {@link DidJwtValidator}, split across two distinct phases:
 *
 * <ol>
 *   <li><strong>Pre-cache validation</strong> ({@link #validateAllowlist(String)}):
 *       Called at fetch time. Checks that the JWT's {@code kid} resolves to a DID URL
 *       on the configured Base Registry allowlist. Fast – no HTTP call. Prevents
 *       malicious JWTs with foreign DIDs from ever entering the cache.</li>
 *   <li><strong>Pre-inject validation</strong> ({@link #validateSignature(String)}):
 *       Called on every request, just before the cached JWT is injected into the Authorization
 *       Request. Fetches the Trust Registry's DID Document fresh and verifies the signature.
 *       Detects key rotations immediately, without waiting for the cache TTL to expire.</li>
 * </ol>
 *
 * <p>Cache eviction on signature failure is the caller's responsibility
 * (see {@link TrustStatementCacheService}).</p>
 *
 * <p>Only active when {@code swiyu.trust-registry.api-url} is configured.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "swiyu.trust-registry", name = "api-url")
public class TrustStatementValidator {

    private final DidJwtValidator trustStatementDidJwtValidator;
    private final DidResolverAdapter didResolverAdapter;
    private final UrlRewriteProperties urlRewriteProperties;

    /**
     * Constructs the validator with the named {@link DidJwtValidator} bean scoped to the
     * Trust Registry allowlist.
     *
     * @param trustStatementDidJwtValidator the JWT validator restricted to the TMS host
     * @param didResolverAdapter            adapter for DID Document resolution
     * @param urlRewriteProperties          URL rewrite mappings for DID resolution
     */
    public TrustStatementValidator(
            @Qualifier("trustStatementDidJwtValidator") DidJwtValidator trustStatementDidJwtValidator,
            DidResolverAdapter didResolverAdapter,
            UrlRewriteProperties urlRewriteProperties) {
        this.trustStatementDidJwtValidator = trustStatementDidJwtValidator;
        this.didResolverAdapter = didResolverAdapter;
        this.urlRewriteProperties = urlRewriteProperties;
    }

    /**
     * Phase 1 – pre-cache allowlist check (no HTTP call).
     *
     * <p>Extracts the DID resolution URL from the JWT's {@code kid} header and validates it
     * against the configured Base Registry allowlist. Call this before storing a freshly
     * fetched trust statement in the cache.</p>
     *
     * @param jwtString the compact serialized Trust Statement JWT
     * @throws TrustStatementValidationException if the JWT is malformed, the {@code kid} is
     *                                            missing or not absolute, or the resolved DID URL
     *                                            is not on the allowlist
     */
    public void validateAllowlist(String jwtString) {
        try {
            String didUrl = trustStatementDidJwtValidator.getAndValidateResolutionUrl(jwtString);
            String didString = trustStatementDidJwtValidator.getDidString(jwtString);
            log.debug("Trust statement allowlist check passed – did={}, url={}", didString, didUrl);
        } catch (JwtValidatorException e) {
            throw new TrustStatementValidationException("Trust statement allowlist check failed", e);
        }
    }

    /**
     * Phase 2 – pre-inject signature verification (HTTP call to DID resolver).
     *
     * <p>Resolves the Trust Registry's DID Document fresh (via {@link DidResolverAdapter})
     * and verifies the JWT signature against the current public key. Call this every time a
     * cached trust statement is about to be injected into the Authorization Request.</p>
     *
     * <p>Because the DID Document is fetched on every call, key rotations on the Trust Registry
     * side are detected immediately – without waiting for the cache TTL to expire.</p>
     *
     * @param jwtString the compact serialized Trust Statement JWT
     * @throws JwtValidatorException if the DID Document cannot be fetched, the key is not found,
     *                               or signature verification fails
     */
    public void validateSignature(String jwtString) {
        String didString = trustStatementDidJwtValidator.getDidString(jwtString);
        log.debug("Verifying trust statement signature for did={}", didString);

        var didDoc = didResolverAdapter.resolveDid(didString, urlRewriteProperties.getUrlMappings());
        trustStatementDidJwtValidator.validateJwt(jwtString, didDoc);
        log.debug("Trust statement signature verification succeeded for did={}", didString);
    }
}
