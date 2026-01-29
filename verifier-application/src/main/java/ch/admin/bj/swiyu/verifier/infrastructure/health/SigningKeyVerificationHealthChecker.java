package ch.admin.bj.swiyu.verifier.infrastructure.health;


import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.service.JwtSigningService;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;

import ch.admin.eid.did_sidekicks.DidDoc;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import ch.admin.eid.did_sidekicks.Jwk;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Health checker that validates the signing capability of the configured verification method.
 *
 * <p>This checker performs the following validations:
 * <ul>
 *   <li>Resolves the DID document for the configured signing key verification method</li>
 *   <li>Verifies that a signer can be provided</li>
 *   <li>Tests the signing and verification process with a dummy JWT</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SigningKeyVerificationHealthChecker extends CachedHealthChecker {

    private static final String HEALTH_DETAIL_FAILED_DIDS = "failedDids";
    private static final String HEALTH_DETAIL_SIGNING_KEY = "signingKeyVerificationMethod";
    private static final String HEALTH_DETAIL_SIGNING_ERROR = "signingError";
    private static final String TEST_JWT_SUBJECT = "health-check-test";

    /** Resolver used to resolve DID documents */
    private final DidResolverFacade didResolverFacade;

    /** Application properties containing the signing key verification method */
    private final ApplicationProperties applicationProperties;

    /** Service used to create signers for JWT signing */
    private final JwtSigningService jwtSigningService;

    /**
     * Performs the health check by validating the signing capability.
     *
     * @param builder The health builder to populate with check results
     */
    @Override
    protected void performCheck(Health.Builder builder) {
        String verificationMethod = applicationProperties.getSigningKeyVerificationMethod();

        // Resolve the DID document
        DidDoc didDoc = resolveDid(verificationMethod);
        if (didDoc == null) {
            builder.down().withDetail(HEALTH_DETAIL_FAILED_DIDS, verificationMethod);
            return;
        }

        // Verify signing capability
        try {
            if (verifySigningCapability(verificationMethod, didDoc)) {
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
     * @param didDoc The resolved DID document
     * @return true if signing and verification succeed, false otherwise
     */
    private boolean verifySigningCapability(String verificationMethod, DidDoc didDoc) throws IllegalArgumentException, JOSEException ,DidSidekicksException, ParseException {

        // Create a test JWT claims set
        JWTClaimsSet testClaims = new JWTClaimsSet.Builder()
                .subject(TEST_JWT_SUBJECT)
                .build();

        // Use the unified signing method
        SignedJWT signedJwt = jwtSigningService.signJwt(testClaims, null, null, verificationMethod);


        // Extract public key from DID document and verify signature
        return verifySignature(signedJwt, didDoc, verificationMethod);
    }

    /**
     * Verifies the signature of a signed JWT using the public key from the DID document.
     *
     * @param signedJwt The signed JWT to verify
     * @param didDoc The DID document containing the public key
     * @param verificationMethod The verification method identifier
     * @return true if signature verification succeeds, false otherwise
     */
    private boolean verifySignature(SignedJWT signedJwt, DidDoc didDoc, String verificationMethod)
            throws JOSEException, DidSidekicksException, ParseException {
        final Jwk jwk = didDoc.getKey(extractFragmentFromVerificationMethod(verificationMethod));
        
        final Map<String, Object> map = new HashMap<>();
        map.put("kty", jwk.getKty());
        map.put("crv", jwk.getCrv());
        map.put("x", jwk.getX());
        map.put("y", jwk.getY());
        map.put("kid", jwk.getKid());
        // Convert to EC public key
        JWK publicKey = JWK.parse(map).toECKey();
        // Create verifier and verify signature
        JWSVerifier verifier = new ECDSAVerifier(publicKey.toECKey());
        return signedJwt.verify(verifier);
    }

    /**
     * Attempts to resolve the DID document for the given verification method.
     *
     * @param verificationMethod The verification method string (may include fragment)
     * @return The resolved DID document, or null if resolution fails
     */
    private DidDoc resolveDid(String verificationMethod) {
        if (verificationMethod == null || verificationMethod.isBlank()) {
            return null;
        }

        try {
            String did = extractDidFromVerificationMethod(verificationMethod);
            return didResolverFacade.resolveDid(did);
        } catch (DidResolverException e) {
            return null;
        }
    }

    /**
     * Extracts the base DID from a verification method by removing the fragment part.
     *
     * <p>Verification methods may contain a fragment identifier (e.g., "did:example:123#key-1").
     * This method removes the fragment to get the base DID (e.g., "did:example:123").
     *
     * @param verificationMethod The verification method string
     * @return The base DID without the fragment
     */
    private String extractDidFromVerificationMethod(String verificationMethod) {
        int fragmentIndex = verificationMethod.indexOf('#');
        return fragmentIndex > 0 ? verificationMethod.substring(0, fragmentIndex) : verificationMethod;
    }

    /**
     * Extracts the fragment part (key identifier) from a verification method.
     *
     * <p>Verification methods may contain a fragment identifier (e.g., "did:example:123#key-1").
     * This method extracts the fragment value (e.g., "key-1") to obtain the pure key identifier.
     * If no fragment is present, the original verification method is returned unchanged.
     *
     * @param verificationMethod The verification method string
     * @return The fragment value without the '#' prefix, or the original string if no fragment is found
     */
    private String extractFragmentFromVerificationMethod(String verificationMethod) {
        int fragmentIndex = verificationMethod.indexOf('#');
        return fragmentIndex > 0 ? verificationMethod.substring(fragmentIndex + 1) : verificationMethod;
    }
}

