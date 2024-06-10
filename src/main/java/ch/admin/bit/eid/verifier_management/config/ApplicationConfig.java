package ch.admin.bit.eid.verifier_management.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Validated
@Data
@ConfigurationProperties(prefix = "application")
public class ApplicationConfig {

    @NotNull
    private Integer verificationTTL;

    @NotNull
    private String oid4vpUrl;
}
