package ch.admin.bj.swiyu.verifier.service.statuslist;

import java.util.HashSet;
import java.util.Set;

import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.UrlRestriction;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifierConfig;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StatusListConfig {
    
    private final ApplicationProperties applicationProperties;
    private final VerificationProperties verificationProperties;

    /**
     * Creates a {@link DidJwtValidator} restricted to the configured registry hosts.
     *
     * <p>The allowlist is derived from the {@code application.accepted-registry-hosts} property,
     * ensuring that trust statement JWTs are only accepted when their {@code kid} resolves
     * to the same host as the configured TMS or Status Registry endpoint.</p>
     *
     * @return the {@link DidJwtValidator} bean named {@code trustStatementDidJwtValidator}
     * @throws IllegalArgumentException if the configured {@code api-url} is malformed
     */
    @Bean
    @Primary
    public DidJwtValidator didJwtValidator() {
        Set<String> hosts = new HashSet<>(applicationProperties.getAcceptedRegistryHosts());
        log.info("Configuring trust statement JWT validator with allowed host: {}", hosts);
        return new DidJwtValidator(new UrlRestriction(hosts));
    }

    /**
     * Creates a {@link TokenStatusListVerifier} which can be configured using the application properties
     * @return the {@link TokenStatusListVerifier} bean named {@code tokenStatusListVerifier}
     */
    @Bean
    public TokenStatusListVerifier tokenStatusListVerifier() {
        return new TokenStatusListVerifier(
            TokenStatusListVerifierConfig.builder()
                .issuerMustMatch(true)
                .expiryMustBePresent(verificationProperties.isExpiryMustBePresent())
                .build());
    }
}
