package ch.admin.bj.swiyu.verifier.service.factory.strategy;

import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jwk.JWK;
import org.springframework.stereotype.Component;

@Component("key")
public class KeyStrategy implements IKeyManagementStrategy{

    @Override
    public JWSSigner createSigner(SignatureConfiguration configuration) throws KeyStrategyException {
        try {
            return fromEC(JWK.parseFromPEMEncodedObjects(configuration.getPrivateKey()).toECKey());
        } catch (JOSEException e) {
            throw new KeyStrategyException("Failed to parse EC Key from PEM.", e);
        }
    }
}
