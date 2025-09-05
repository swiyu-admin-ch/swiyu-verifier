package ch.admin.bj.swiyu.verifier.service.factory.strategy;

import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton;
import com.nimbusds.jose.jwk.ECKey;

import java.security.Provider;
import java.security.interfaces.ECPrivateKey;

public interface IKeyManagementStrategy {
    JWSSigner createSigner(SignatureConfiguration signatureConfiguration) throws KeyStrategyException;

    /**
     * @param privateKey The private key loaded with ECKey.load from the keystore
     * @param provider   Provider like Sun PKCS11 Provider, already used to initialize the keystore
     * @return a newly created Signing Support
     * @throws JOSEException if the Signer could not be created with the provided key & provider
     */
    default JWSSigner fromEC(ECKey privateKey, Provider provider) throws JOSEException {
        var signer = new ECDSASigner(privateKey);
        signer.getJCAContext().setProvider(provider);
        return signer;
    }

    /**
     * Alternative creator for HSM
     *
     * @param privateKey The private key loaded from the keystore. The keystore must have a certificate for it to work properly.
     * @param provider   Provider like Securosys Primus Provider, already used to initialize the keystore
     * @return a newly created Signing Support
     * @throws JOSEException if the Signer could not be created with the provided key & provider
     */
    default JWSSigner fromEC(ECPrivateKey privateKey, Provider provider) throws JOSEException {
        var signer = new ECDSASigner(privateKey);
        signer.getJCAContext().setProvider(provider);
        return signer;
    }

    /**
     * @param privateKey The private key loaded with ECKey.load from a string using bouncycastle
     * @return a newly created Signing Support
     * @throws JOSEException if the Signer could not be created with the provided key & provider
     */
    default JWSSigner fromEC(ECKey privateKey) throws JOSEException {
        return fromEC(privateKey, BouncyCastleProviderSingleton.getInstance());
    }
}
