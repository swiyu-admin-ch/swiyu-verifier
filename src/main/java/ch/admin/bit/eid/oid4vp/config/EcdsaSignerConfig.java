package ch.admin.bit.eid.oid4vp.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class EcdsaSignerConfig {

    private final ApplicationProperties applicationProperties;

    @Bean
    public ECDSASigner ecdsaSigner() throws JOSEException {
        return new ECDSASigner(ECKey.parseFromPEMEncodedObjects(applicationProperties.getSigningKey()).toECKey());
    }
}
