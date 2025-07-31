package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import ch.admin.bj.swiyu.verifier.common.config.SignerProvider;
import ch.admin.bj.swiyu.verifier.service.factory.KeyManagementStrategyFactory;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class SignatureService {

    private final ApplicationProperties applicationProperties;
    private final KeyManagementStrategyFactory keyManagementStrategyFactory;

    public SignerProvider createDefaultSignerProvider() throws Exception {
        var defaultSignatureConfiguration = toSignatureConfiguration(applicationProperties);
        return createSignerProvider(defaultSignatureConfiguration);
    }

    public SignerProvider createSignerProvider(SignatureConfiguration signatureConfiguration) throws Exception {
        return new SignerProvider(keyManagementStrategyFactory.getStrategy(signatureConfiguration.getKeyManagementMethod())
                .createSigner(signatureConfiguration));
    }

    /**
     * Mapping allowing to use same logic as issuer service in most parts
     * @return SignatureConfiguration built from the given application properties
     */
    public static SignatureConfiguration toSignatureConfiguration(ApplicationProperties applicationProperties) {
        return SignatureConfiguration.builder()
                .keyManagementMethod(applicationProperties.getKeyManagementMethod())
                .privateKey(applicationProperties.getSigningKey())
                .hsm(applicationProperties.getHsm())
                .pkcs11Config(applicationProperties.getHsm().getPkcs11Config())
                .verificationMethod(applicationProperties.getSigningKeyVerificationMethod())
                .build();
    }
}
