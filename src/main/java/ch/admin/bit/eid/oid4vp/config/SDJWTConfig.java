package ch.admin.bit.eid.oid4vp.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Validated
@ConfigurationProperties(prefix = "application.key.sdjwt")
@Data
public class SDJWTConfig {

    @NotNull
    private String publicKey;
}
