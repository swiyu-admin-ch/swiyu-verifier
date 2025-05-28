package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;


@Validated
@Data
@Configuration
@ConfigurationProperties(prefix = "verification")
public class VerificationProperties {

    /**
     * Window for issuing time of the holder binding proof
     */
    @NotNull
    private int acceptableProofTimeWindowSeconds;

    /**
     * Size Limit of fetched objects like did document or status list.
     */
    @NotNull
    private int objectSizeLimit;
}
