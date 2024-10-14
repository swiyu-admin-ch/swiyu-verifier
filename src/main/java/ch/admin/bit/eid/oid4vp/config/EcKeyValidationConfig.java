package ch.admin.bit.eid.oid4vp.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
@Slf4j
public class EcKeyValidationConfig {

    private final ApplicationProperties applicationProperties;

    @PostConstruct
    public void validateEcKey() {
        var signingKey = applicationProperties.getSigningKey();
        if (signingKey == null) {
            log.warn("No signing key configured");
            return;
        }
        try {
            ECKey.parseFromPEMEncodedObjects(signingKey).toECKey();
        } catch (JOSEException e) {
            // we want to inform the user that parsing of signing key failed (at startup).
            // but the app must not crash, because it is possible that it is on purpose, and he returns an unsigned request object
            log.warn("Failed to parse ECDSA signing key", e);
        }
    }
}
