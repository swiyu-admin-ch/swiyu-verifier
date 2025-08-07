package ch.admin.bj.swiyu.verifier.service.factory.strategy;

import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPrivateKey;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.SIGNING_KEY_CACHE;

@Component("securosys")
public class SecurosysStrategy implements IKeyManagementStrategy {

    @Override
    @Cacheable(SIGNING_KEY_CACHE)
    public JWSSigner createSigner(SignatureConfiguration configuration) throws KeyStrategyException {
        try {
            final var baos = new ByteArrayOutputStream();
            // Create ad-hoc configuration
            (new PrintStream(baos)).println(
                    configuration.getHsm().getSecurosysStringConfig()
            );
            final var bais = new ByteArrayInputStream(baos.toByteArray());
            final var provider = (Provider) Class.forName("com.securosys.primus.jce.PrimusProvider").getDeclaredConstructor().newInstance();

            Security.addProvider(provider);
            var hsmKeyStore = KeyStore.getInstance("Primus");
            hsmKeyStore.load(bais, null);

            // Loading the ECKey does not work for securosys provider, it does things different than expected by nimbus
            var privateKey = (ECPrivateKey) hsmKeyStore.getKey(configuration.getHsm().getKeyId(), configuration.getHsm().getUserPin().toCharArray());
            return fromEC(privateKey, provider);
        } catch (InstantiationException | ClassNotFoundException | KeyStoreException | IOException |
                 NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | JOSEException |
                 NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new KeyStrategyException("Failed to load EC Key from securosys primus JCE.", e);
        }
    }
}
