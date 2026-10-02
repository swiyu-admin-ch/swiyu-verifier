package ch.admin.bj.swiyu.verifier.common.config;

import ch.admin.bj.swiyu.verifier.SignatureConfiguration;
import lombok.Setter;

import java.util.List;

/**
 * Signature configuration that uses a private key directly for signing
 * and does not rely on an HSM or additional signing key configurations.
 *
 * <p>This implementation uses {@code "key"} as its key management method.
 * HSM-related configuration is not supported, and no additional signing
 * keys are maintained.</p>
 */
public class KeyOnlySignatureConfiguration implements SignatureConfiguration {

    @Setter
    private String verificationMethod;

    @Setter
    private String privateKey;

    @Override
    public String getVerificationMethod() {
        return this.verificationMethod;
    }

    @Override
    public String getPrivateKey() {
        return this.privateKey;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code false}, as this configuration does not use an HSM
     */
    @Override
    public boolean supportsHSM() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code "key"}, indicating that a private key is used directly
     */
    @Override
    public String getKeyManagementMethod() {
        return "key";
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code false}, as this configuration does not support
     * additional signing keys
     */
    @Override
    public boolean supportsSigningKeys() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code null}, as HSM configuration is not supported
     */
    @Override
    public HSMProperties getHsm() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code null}, as PKCS#11 configuration is not supported
     */
    @Override
    public String getPkcs11Config() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @return an empty list, as this configuration does not contain
     * additional signing keys
     */
    @Override
    public List<KeyOnlySignatureConfiguration> getSigningKeys() {
        return List.of();
    }
}

