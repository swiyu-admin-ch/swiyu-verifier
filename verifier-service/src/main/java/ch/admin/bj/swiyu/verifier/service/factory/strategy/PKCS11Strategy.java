package ch.admin.bj.swiyu.verifier.service.factory.strategy;

import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jwk.ECKey;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;

@Component("pkcs11")
public class PKCS11Strategy implements IKeyManagementStrategy{
    @Override
    public JWSSigner createSigner(SignatureConfiguration configuration) throws KeyStrategyException {
        try {
        Provider provider = Security.getProvider("SunPKCS11").configure(configuration.getHsm().getPkcs11Config());
        Security.addProvider(provider);
        KeyStore hsmKeyStore = KeyStore.getInstance("PKCS11", provider);
        hsmKeyStore.load(null, configuration.getHsm().getUserPin().toCharArray());
        var privateKey = ECKey.load(hsmKeyStore, configuration.getHsm().getKeyId(), configuration.getHsm().getUserPin().toCharArray());

        return fromEC(privateKey, provider);
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | JOSEException e) {
            throw new KeyStrategyException("Failed to load EC Key from PKCS11 JCE.", e);
        }
    }
}
