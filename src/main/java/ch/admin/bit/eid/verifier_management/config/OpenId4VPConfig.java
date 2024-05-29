package ch.admin.bit.eid.verifier_management.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Validated
@Data
@ConfigurationProperties(prefix = "verifier-openid4vp")
public class OpenId4VPConfig {

    @NotNull
    private String requestObjectPattern;

    @NotNull
    private String requestObjectResponsePattern;
}
