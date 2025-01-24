package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

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
    private String clientName;

    @NotNull
    private String signingKey;

    @NotNull
    private String signingKeyVerificationMethod;

    @NotNull
    private String keyManagementMethod;

    private HSMProperties hsm;

    private String logoUri;

}

