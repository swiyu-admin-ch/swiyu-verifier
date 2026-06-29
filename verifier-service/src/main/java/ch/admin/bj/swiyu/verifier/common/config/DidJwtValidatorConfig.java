package ch.admin.bj.swiyu.verifier.common.config;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.UrlRestriction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides the general-purpose {@link DidJwtValidator} for credential JWT signature validation.
 *
 * <p>This configuration enforces validation for all credential JWTs:
 * <ul>
 *   <li>The {@code iss} claim is completely ignored for key resolution. Trust is established
 *       exclusively via the absolute {@code kid} header value.</li>
 *   <li>Relative {@code kid} values are rejected outright.</li>
 *   <li>DID resolution is restricted to the configured
 *       {@code application.accepted-registry-hosts} allowlist to prevent SSRF / "Phone Home"
 *       attacks (EIDSEC-141).</li>
 * </ul>
 *
 * <p>This bean is always active and independent of the optional Trust Registry configuration.
 * It uses the same allowlist as the trust-registry-specific validator to ensure consistent
 * host restrictions across the entire verification pipeline.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DidJwtValidatorConfig {

    private final ApplicationProperties applicationProperties;

    /**
     * Creates a {@link DidJwtValidator} restricted to the configured base registry hosts.
     *
     * <p>The allowlist is derived from the {@code application.accepted-registry-hosts}
     * property. Only DID documents hosted on these registries can be used as the source
     * for credential issuer public keys.</p>
     *
     * @return the {@link DidJwtValidator} bean named {@code credentialDidJwtValidator}
     * @throws IllegalArgumentException if {@code accepted-registry-hosts} is empty
     */
    @Bean
    public DidJwtValidator credentialDidJwtValidator() {
        Set<String> hosts = new HashSet<>(applicationProperties.getAcceptedRegistryHosts());
        log.info("Configuring credential JWT validator with allowed registry hosts: {}", hosts);
        return new DidJwtValidator(new UrlRestriction(hosts));
    }
}

