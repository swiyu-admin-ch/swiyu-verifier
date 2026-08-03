package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Validated
@Data
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

    /**
     * Indicates whether the expiry must be present in the verification
     * (Change this at your own risk if you want to use older versions of the issuer with status list without exp)
     */
    @NotNull
    private boolean expiryMustBePresent = true;
}
