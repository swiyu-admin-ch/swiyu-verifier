package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Validated
@Data
@Configuration
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties {

    @NotNull
    private String externalUrl;

    @NotNull
    private String clientId;

    @NotNull
    private String clientIdScheme;

    @NotNull
    private String signingKey;

    @NotNull
    private String signingKeyVerificationMethod;


    @PostConstruct
    public void validateEcKey() {
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

