package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

/**
 * Information to sign the Request Object
 */
@Data
@Builder
public class SignatureConfiguration {
    /**
     * Method of signing key management
     */
    @NotNull
    private String keyManagementMethod;

    /**
     * Private Key, if the key is not managed by HSM
     * This includes vault or just mounted as environment variable
     */
    private String privateKey;

    /**
     * Configuration Information for connecting to HSM and using an HSM Key
     */
    private HSMProperties hsm;

    /**
     * Location of the config file, see the <a href="https://docs.oracle.com/en/java/javase/21/security/pkcs11-reference-guide1.html">official java documentation</a>
     */
    private String pkcs11Config;

    /**
     * The id of the verification method in the did document with which a verifier can check the issued VC
     * In did tdw/webvc this is the full did#fragment
     */
    @NotEmpty
    private String verificationMethod;
}
