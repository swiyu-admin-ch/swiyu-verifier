package ch.admin.bit.eid.oid4vp.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

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
    private String clientName;

    private String signingKey;

    private String signingKeyVerificationMethod;

    private String logoUri;

}

