package ch.admin.bit.eid.oid4vp.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

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
    private String logoUri;
}

