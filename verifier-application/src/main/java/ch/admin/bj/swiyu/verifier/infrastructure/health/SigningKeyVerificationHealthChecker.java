package ch.admin.bj.swiyu.verifier.infrastructure.health;


import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.service.SignatureService;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverException;
import ch.admin.eid.did_sidekicks.DidDoc;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import ch.admin.eid.did_sidekicks.Jwk;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.text.ParseException;

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
    private final DidResolverAdapter didResolverAdapter;

    /** Application properties containing the signing key verification method */
    private final ApplicationProperties applicationProperties;

    /** Service used to create signers for JWT signing */
    private final SignatureService signatureService;

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
     * @throws Exception if any error occurs during signing or verification
     */
    private boolean verifySigningCapability(String verificationMethod, DidDoc didDoc) throws Exception {
        // Create and validate signer provider
        var signerProvider = signatureService.createDefaultSignerProvider();
        if (!signerProvider.canProvideSigner()) {
            return false;
        }

        // Create a test JWT with dummy claims
        SignedJWT testJwt = createTestJwt();

        // Sign the JWT
        signerProvider.getSigner().sign(testJwt.getHeader(), testJwt.getSigningInput());

        // Extract public key from DID document and verify signature
        return verifySignature(testJwt, didDoc, verificationMethod);
    }

    /**
     * Creates a test JWT with a dummy header and payload.
     *
     * @return A new SignedJWT instance for testing
     */
    private SignedJWT createTestJwt() {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256).build();
        JWTClaimsSet payload = new JWTClaimsSet.Builder()
                .subject(TEST_JWT_SUBJECT)
                .build();
        return new SignedJWT(header, payload);
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
            throws JOSEException, DidSidekicksException, ParseException {        // Extract the JWK from the DID document
        Jwk jwk = didDoc.getKey(verificationMethod);

        // Parse and convert to EC public key
        JWK publicKey = JWK.parse(jwk.toString()).toECKey();

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
            return didResolverAdapter.resolveDid(did);
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
}

