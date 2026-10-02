package ch.admin.bj.swiyu.verifier.common.config;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtVcValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerifierBeansConfig {

    @Bean
    public SdJwtVcValidator sdJwtVcValidator(DidJwtValidator didJwtValidator) {
        return new SdJwtVcValidator(didJwtValidator);
    }
}
