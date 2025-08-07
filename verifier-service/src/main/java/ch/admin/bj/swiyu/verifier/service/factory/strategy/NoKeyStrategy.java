package ch.admin.bj.swiyu.verifier.service.factory.strategy;

import ch.admin.bj.swiyu.verifier.common.config.SignatureConfiguration;
import com.nimbusds.jose.JWSSigner;
import org.springframework.stereotype.Component;

@Component("none")
public class NoKeyStrategy implements IKeyManagementStrategy{

    @Override
    public JWSSigner createSigner(SignatureConfiguration configuration) {
        return null;
    }
}
