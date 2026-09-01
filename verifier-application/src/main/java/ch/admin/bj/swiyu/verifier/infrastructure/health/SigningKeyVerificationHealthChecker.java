package ch.admin.bj.swiyu.verifier.infrastructure.health;


import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.service.JwtSigningService;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Health checker that validates the signing capability of the configured verification method.
 *
 * <p>This checker performs the following validations:
 * <ul>
 *   <li>Resolves the DID document for the configured signing key verification method</li>
 *   <li>Verifies that a signer can be provided</li>
 *   <li>Tests the signing and verification process with a dummy JWT</li>
 * </ul>
 *
 * <p>The check can be disabled via {@code management.health.signing-key-verification-enabled=false}
 * (env: {@code SIGNING_KEY_VERIFICATION_ENABLED=false}). When disabled, the check reports
 * {@code UP} with {@code signingKeyVerificationMethod: disabled}.</p>
 */
@Component
@RequiredArgsConstructor
public class SigningKeyVerificationHealthChecker extends CachedHealthChecker {

    private static final String HEALTH_DETAIL_SIGNING_KEY = "signingKeyVerificationMethod";
    private static final String HEALTH_DETAIL_SIGNING_ERROR = "signingError";
    private static final String TEST_JWT_SUBJECT = "health-check-test";

    /** Resolver used to resolve DID documents */
    private final DidResolverFacade didResolverFacade;

    /** Application properties containing the signing key verification method */
    private final ApplicationProperties applicationProperties;

    /** Service used to create signers for JWT signing */
    private final JwtSigningService jwtSigningService;

    /** Health check configuration properties */
    private final HealthCheckProperties healthCheckProperties;

    @Override
    protected boolean isEnabled() {
        return healthCheckProperties.isSigningKeyVerificationEnabled();
    }

    /**
     * Returns UP with {@code signingKeyVerificationMethod: disabled} when the check is disabled via configuration.
     */
    @Override
    protected Health buildDisabledHealth() {
        return Health.up().withDetail(HEALTH_DETAIL_SIGNING_KEY, "disabled").build();
    }

    /**
     * Performs the health check by validating the signing capability.
     *
     * <p>If no verification method is configured (blank), the check is skipped and UP is reported,
     * since dynamic key management does not require a statically configured key.</p>
     *
     * @param builder The health builder to populate with check results
     */
    @Override
    protected void performCheck(Health.Builder builder) {
        String verificationMethod = applicationProperties.getSigningKeyVerificationMethod();

        if (verificationMethod == null || verificationMethod.isBlank()) {
            builder.up().withDetail(HEALTH_DETAIL_SIGNING_KEY, "not configured");
            return;
        }

        try {
            if (verifySigningCapability(verificationMethod)) {
                builder.up().withDetail(HEALTH_DETAIL_SIGNING_KEY, verificationMethod);
            } else {
                builder.down().withDetail(HEALTH_DETAIL_SIGNING_KEY,
                        "Verification failed for " + verificationMethod);
            }
        } catch (Exception e) {
            builder.down()
                    .withDetail(HEALTH_DETAIL_SIGNING_ERROR, e.getMessage())
                    .withDetail(HEALTH_DETAIL_SIGNING_KEY, verificationMethod);
        }
    }

    /**
     * Verifies that the signing key can sign a JWT and the signature can be verified.
     *
     * <p>This method:
     * <ol>
     *   <li>Creates a signer provider</li>
     *   <li>Signs a test JWT</li>
     *   <li>Extracts the public key from the DID document</li>
     *   <li>Verifies the signature using the public key</li>
     * </ol>
     *
     * @param verificationMethod The verification method identifier
     * @return true if signing and verification succeed, false otherwise
     */
    private boolean verifySigningCapability(String verificationMethod)
            throws IllegalArgumentException, JOSEException, DidResolverException {

        if (verificationMethod == null || verificationMethod.isBlank()) {
            return false;
        }

        // Resolve JWK directly via facade
        JWK jwk = didResolverFacade.resolveKey(verificationMethod);

        // Create a test JWT claims set
        JWTClaimsSet testClaims = new JWTClaimsSet.Builder()
                .subject(TEST_JWT_SUBJECT)
                .build();

        // Sign a JWT using the configured signing key
        SignedJWT signedJwt = jwtSigningService.signJwt(testClaims, ConfigurationOverride.builder().verificationMethod(verificationMethod).build());

        // Verify signature using the resolved JWK
        return verifySignature(signedJwt, jwk);
    }

    /**
     * Verifies the signature of a signed JWT using the public key from the DID document.
     *
     * @param signedJwt The signed JWT to verify
     * @param jwk The Jwk containing the public key
     * @return true if signature verification succeeds, false otherwise
     */
    private boolean verifySignature(SignedJWT signedJwt, JWK jwk)
            throws JOSEException {

        // Create verifier and verify signature
        JWSVerifier verifier = new ECDSAVerifier(jwk.toECKey());
        return signedJwt.verify(verifier);
    }
}
