package ch.admin.bj.swiyu.verifier.common.config;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.UrlRestriction;
import ch.admin.bj.swiyu.sdjwtvalidator.SdJwtVcValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;

/**
 * Spring configuration for the centralized JWT and SD-JWT VC validator beans.
 * <p>
 * Creates {@link DidJwtValidator} and {@link SdJwtVcValidator} as singletons,
 * wiring in the configured {@link UrlRestriction} to enforce the swiyu Base Registry allowlist
 * and prevent CSRF / "phone home" attacks during DID-based key resolution.
 * </p>
 */
@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
public class ValidatorConfig {

    /**
     * Creates a {@link DidJwtValidator} restricted to the configured allowed identifier hosts.
     *
     * @param applicationProperties application-level configuration containing the allowlist
     * @return a configured {@link DidJwtValidator} instance
     */
    @Bean
    public DidJwtValidator didJwtValidator(ApplicationProperties applicationProperties) {
        var allowedHosts = new HashSet<>(applicationProperties.getAcceptedIdentifierHosts());
        return new DidJwtValidator(new UrlRestriction(allowedHosts));
    }

    /**
     * Creates a {@link SdJwtVcValidator} accepting both {@code dc+sd-jwt} and {@code vc+sd-jwt}
     * type headers for migration compatibility.
     *
     * @param didJwtValidator the underlying DID JWT validator
     * @return a configured {@link SdJwtVcValidator} instance
     */
    @Bean
    public SdJwtVcValidator sdJwtVcValidator(DidJwtValidator didJwtValidator) {
        return new SdJwtVcValidator(didJwtValidator,
                java.util.Set.of(SdJwtVcValidator.TYP_DC_SD_JWT, SdJwtVcValidator.TYP_VC_SD_JWT));
    }
}

