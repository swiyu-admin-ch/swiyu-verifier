package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import ch.admin.bj.swiyu.verifier.common.util.SignerProvider;
import ch.admin.bj.swiyu.verifier.service.factory.KeyManagementStrategyFactory;
import ch.admin.bj.swiyu.verifier.service.factory.strategy.KeyStrategyException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class JwtSigningService {

    private final ApplicationProperties applicationProperties;
    private final KeyManagementStrategyFactory keyManagementStrategyFactory;

    /**
     * Signs the provided JWT claims set using the specified or default signing configuration.
     * If keyId and keyPin are provided, they override the default key settings; otherwise, defaults are used.
     *
     * @param claimsSet the JWT claims to sign
     * @param keyId optional key ID to override the default; if null, default is used
     * @param keyPin optional key PIN to override the default; if null, default is used
     * @param verificationMethod the verification method for the JWS header (e.g., a DID URL)
     * @return the signed JWT object
     * @throws IllegalArgumentException if invalid arguments are provided
     * @throws IllegalStateException if the signer provider cannot be initialized or no signing key is available
     * @throws JOSEException if the signing operation fails
     */
    public SignedJWT signJwt(JWTClaimsSet claimsSet, String keyId, String keyPin, String verificationMethod)
            throws IllegalArgumentException, JOSEException {
        SignerProvider signerProvider;
        try {
            if (keyId != null && keyPin != null) {
                signerProvider = createSignerProvider(keyId, keyPin);
            } else {
                signerProvider = createDefaultSignerProvider();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize signature provider. This is probably because the key could not be loaded.", e);
        }

        if (!signerProvider.canProvideSigner()) {
            log.error("Upstream system error. Upstream system requested presentation to be signed despite the verifier not being configured for it");
            throw new IllegalStateException("Presentation was configured to be signed, but no signing key was configured.");
        }

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(verificationMethod)
                .type(new JOSEObjectType("oauth-authz-req+jwt"))
                .build();
        SignedJWT signedJwt = new SignedJWT(header, claimsSet);
        signedJwt.sign(signerProvider.getSigner());

        return signedJwt;
    }

    /**
     * Creates a default {@link SignerProvider} using the keyId and keyPin from the application properties.
     *
     * @return a configured {@link SignerProvider}
     * @throws IllegalArgumentException if invalid arguments are provided
     * @throws KeyStrategyException     if key strategy creation fails
     */
    private SignerProvider createDefaultSignerProvider() throws IllegalArgumentException, KeyStrategyException {
        var defaultSignatureConfiguration = toSignatureConfiguration(applicationProperties);
        return createSignerProvider(defaultSignatureConfiguration);
    }

    /**
     * Creates a {@link SignerProvider} using the default settings, but overrides keyId and keyPin.
     *
     * @param keyId  the key identifier to use
     * @param keyPin the key PIN to use
     * @return a configured {@link SignerProvider}
     * @throws IllegalArgumentException if invalid arguments are provided
     * @throws KeyStrategyException     if key strategy creation fails
     */
    private SignerProvider createSignerProvider(String keyId, String keyPin) throws IllegalArgumentException, KeyStrategyException {
        var defaultSignatureConfiguration = toSignatureConfiguration(applicationProperties);
        defaultSignatureConfiguration.getHsm().setKeyId(keyId);
        defaultSignatureConfiguration.getHsm().setKeyPin(keyPin);
        return createSignerProvider(defaultSignatureConfiguration);
    }

    private SignerProvider createSignerProvider(SignatureConfiguration signatureConfiguration) throws IllegalArgumentException, KeyStrategyException {
        return new SignerProvider(keyManagementStrategyFactory.getStrategy(signatureConfiguration.getKeyManagementMethod())
                .createSigner(signatureConfiguration));
    }

    /**
     * Mapping allowing to use same logic as issuer service in most parts
     *
     * @return SignatureConfiguration built from the given application properties
     */
    private static SignatureConfiguration toSignatureConfiguration(
            ApplicationProperties applicationProperties) {
        return SignatureConfiguration.builder()
                .keyManagementMethod(applicationProperties.getKeyManagementMethod())
                .privateKey(applicationProperties.getSigningKey())
                .hsm(applicationProperties.getHsm())
                .pkcs11Config(applicationProperties.getHsm().getPkcs11Config())
                .verificationMethod(applicationProperties.getSigningKeyVerificationMethod())
                .build();
    }
}
