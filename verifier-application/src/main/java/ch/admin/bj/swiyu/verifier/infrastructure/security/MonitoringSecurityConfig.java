package ch.admin.bj.swiyu.verifier.infrastructure.security;

import ch.admin.bj.swiyu.verifier.infrastructure.config.MonitoringBasicAuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monitoring.basic-auth.enabled")
public class MonitoringSecurityConfig {

    private static final String BASIC_AUTH_ROLE_NAME = UUID.randomUUID().toString();
    private final MonitoringBasicAuthProperties basicAuthProperties;

    @Bean
    public UserDetailsService userDetailsService() {
        PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withUsername(basicAuthProperties.getUsername())
                .password(passwordEncoder.encode(basicAuthProperties.getPassword()))
                .roles(BASIC_AUTH_ROLE_NAME)
                .build());
        return manager;
    }

    @Bean
    public SecurityFilterChain securityFilterChainForMonitoring(HttpSecurity http) throws Exception {
        return http
                // Apply security settings to API endpoints, Swagger UI, API documentation and actuator endpoints
                .securityMatchers(matchers -> matchers.requestMatchers("/actuator/prometheus"))
                // Disable CSRF protection since this is a stateless API (no browser sessions)
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/prometheus").hasRole(BASIC_AUTH_ROLE_NAME)
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
