/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.common.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import com.nimbusds.jose.jwk.ECKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;

/**
 * Configures a JWS Singer. Used in Issuer management, OID4VCI and Verifier OID4VP
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SignerConfig {
    private final ApplicationProperties applicationProperties;

    /**
     * @return A Signing Provider used to sign JWTs.
     * @throws Exception if the SigningProvider can not be created.
     */
    @Bean
    public SignerProvider defaultSigner() throws Exception {
        return switch (applicationProperties.getKeyManagementMethod()) {
            case "none" -> {
                log.warn("Starting without any signing key configured. Attempting to provided signed presentation requests will cause errors!");
                yield new SignerProvider(null);
            }
            case "key" ->
                // We are currently only supporting EC Keys
                    fromEC(ECKey.parseFromPEMEncodedObjects(applicationProperties.getSigningKey()).toECKey());

            case "pkcs11" -> {
                Provider provider = Security.getProvider("SunPKCS11").configure(applicationProperties.getHsm().getPkcs11Config());
                Security.addProvider(provider);
                var hsmKeyStore = KeyStore.getInstance("PKCS11", provider);
                hsmKeyStore.load(null, applicationProperties.getHsm().getUserPin().toCharArray());
                var privateKey = ECKey.load(hsmKeyStore, applicationProperties.getHsm().getKeyId(), applicationProperties.getHsm().getUserPin().toCharArray());

                yield fromEC(privateKey, provider);
            }
            case "securosys" -> {
                // Inspired by https://docs.securosys.com/assets/files/ProxyConfigSample-1a86820104d8ada67f90d5218f2db5f8.java
                // Requires the key to be created together with a self-signed certificate as described in https://docs.securosys.com/primus-tools/Use-Cases/certificate-sign-request
                // Dynamic Imported Primus
                final var baos = new ByteArrayOutputStream();
                // Create ad-hoc configuration
                (new PrintStream(baos)).println(
                        applicationProperties.getHsm().getSecurosysStringConfig()
                );
                final var bais = new ByteArrayInputStream(baos.toByteArray());
                final var provider = (Provider) Class.forName("com.securosys.primus.jce.PrimusProvider").getDeclaredConstructor().newInstance();

                Security.addProvider(provider);
                var hsmKeyStore = KeyStore.getInstance("Primus");
                hsmKeyStore.load(bais, null);

                // Loading the ECKey does not work for securosys provider, it does things different than expected by nimbus
                var privateKey = (ECPrivateKey) hsmKeyStore.getKey(applicationProperties.getHsm().getKeyId(), applicationProperties.getHsm().getUserPin().toCharArray());
                yield fromEC(privateKey, provider);
            }
            default ->
                    throw new IllegalArgumentException(String.format("Key management method \"%s\" not supported", applicationProperties.getKeyManagementMethod()));

        };
    }

    /**
     * @param privateKey The private key loaded with ECKey.load from the keystore
     * @param provider   Provider like Sun PKCS11 Provider, already used to initialize the keystore
     * @return a newly created Signing Support
     * @throws JOSEException if the Signer could not be created with the provided key & provider
     */
    private SignerProvider fromEC(ECKey privateKey, Provider provider) throws JOSEException {
        var signer = new ECDSASigner(privateKey);
        signer.getJCAContext().setProvider(provider);
        return new SignerProvider(signer);
    }

    /**
     * Alternative creator for HSM
     *
     * @param privateKey The private key loaded from the keystore. The keystore must have a certificate for it to work properly.
     * @param provider   Provider like Securosys Primus Provider, already used to initialize the keystore
     * @return a newly created Signing Support
     * @throws JOSEException if the Signer could not be created with the provided key & provider
     */
    private SignerProvider fromEC(ECPrivateKey privateKey, Provider provider) throws JOSEException {
        var signer = new ECDSASigner(privateKey);
        signer.getJCAContext().setProvider(provider);
        return new SignerProvider(signer);
    }

    /**
     * @param privateKey The private key loaded with ECKey.load from a string using bouncycastle
     * @return a newly created Signing Support
     * @throws JOSEException if the Signer could not be created with the provided key & provider
     */
    private SignerProvider fromEC(ECKey privateKey) throws JOSEException {
        return fromEC(privateKey, BouncyCastleProviderSingleton.getInstance());
    }
}
