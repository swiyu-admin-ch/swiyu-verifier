package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import ch.admin.bj.swiyu.verifier.common.config.SignerProvider;
import ch.admin.bj.swiyu.verifier.service.factory.KeyManagementStrategyFactory;
import ch.admin.bj.swiyu.verifier.service.factory.strategy.KeyStrategyException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SignatureService {

    private final ApplicationProperties applicationProperties;
    private final KeyManagementStrategyFactory keyManagementStrategyFactory;

    /**
     * Creates a default {@link SignerProvider} using the keyId and keyPin from the application properties.
     *
     * @return a configured {@link SignerProvider}
     * @throws IllegalArgumentException if invalid arguments are provided
     * @throws KeyStrategyException if key strategy creation fails
     */
    public SignerProvider createDefaultSignerProvider() throws IllegalArgumentException, KeyStrategyException {
        var defaultSignatureConfiguration = toSignatureConfiguration(applicationProperties);
        return createSignerProvider(defaultSignatureConfiguration);
    }

    /**
     * Creates a {@link SignerProvider} using the default settings, but overrides keyId and keyPin.
     *
     * @param keyId the key identifier to use
     * @param keyPin the key PIN to use
     * @return a configured {@link SignerProvider}
     * @throws IllegalArgumentException if invalid arguments are provided
     * @throws KeyStrategyException if key strategy creation fails
     */
    public SignerProvider createSignerProvider(String keyId, String keyPin) throws IllegalArgumentException, KeyStrategyException {
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
