package ch.admin.bj.swiyu.verifier;

import ch.admin.bj.swiyu.verifier.common.config.HSMProperties;
import ch.admin.bj.swiyu.verifier.common.config.KeyOnlySignatureConfiguration;

import java.util.List;

/**
 * Defines the configuration required for signature generation and verification.
 *
 * <p>The configuration can describe either an HSM-based signing setup,
 * directly configured signing keys, or both. It also provides the
 * verification method used to validate signatures.</p>
 */
public interface SignatureConfiguration {

    /**
     * Indicates whether an HSM (Hardware Security Module) is configured
     * and supported for signing operations.
     * This can be through a direct JCA plugin or through PKCS#11.
     *
     * @return {@code true} if HSM signing is supported; {@code false} otherwise
     */
    boolean supportsHSM();

    /**
     * Indicates whether signing keys are configured and supported.
     *
     * @return {@code true} if signing keys are supported; {@code false} otherwise
     */
    boolean supportsSigningKeys();

    /**
     * Returns the configured key management method.
     *
     * @return the key management method
     */
    String getKeyManagementMethod();

    /**
     * Returns the configured private key.
     *
     * @return the private key
     */
    String getPrivateKey();

    /**
     * Sets the private key to be used for signing.
     *
     * @param privateKey the private key
     */
    void setPrivateKey(String privateKey);

    /**
     * Returns the HSM configuration.
     *
     * @return the HSM properties, or {@code null} if no HSM is configured
     */
    HSMProperties getHsm();

    /**
     * Returns the PKCS#11 configuration used to access the HSM.
     *
     * @return the PKCS#11 configuration
     */
    String getPkcs11Config();

    /**
     * Returns the method used to verify signatures.
     *
     * @return the signature verification method
     */
    String getVerificationMethod();

    /**
     * Sets the method used to verify signatures.
     *
     * @param verificationMethod the signature verification method
     */
    void setVerificationMethod(String verificationMethod);

    /**
     * Returns the configured signing keys.
     *
     * @return the list of signing key configurations
     */
    List<KeyOnlySignatureConfiguration> getSigningKeys();
}
