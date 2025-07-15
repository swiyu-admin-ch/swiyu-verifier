/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.infrastructure.security;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {
    private final OAuth2ResourceServerProperties oAuth2ResourceServerProperties;


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Apply security settings to API endpoints, Swagger UI, API documentation and actuator endpoints
            .securityMatchers(matchers -> matchers.requestMatchers("/oid4vp/**", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**"))
            // Disable CSRF protection since this is a stateless API (no browser sessions)
            .csrf(AbstractHttpConfigurer::disable)
            // Define authorization rules for different endpoints
            .authorizeHttpRequests(authorize -> authorize
                    // Allow public access to all endpoints as auth is handled via filter
                    .anyRequest().permitAll()
            );

        return http.build();
    }

    @Bean
    @Order(100)
    public SecurityFilterChain managmentSecurityFilterChain(HttpSecurity http) throws Exception {
        if (!hasAnyOAuthProperty()){
            return allowAccessTo(http, "/management/**").build();
        }
        http.securityMatchers(matchers -> matchers.requestMatchers("/management/**"))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated());
        if (hasOAuthJwtProperty()) {
            http.oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(Customizer.withDefaults()));
        } else if (hasOAuthOpaqueTokenProperty()) {
            http.oauth2ResourceServer(oauth2 ->
                    oauth2.opaqueToken(Customizer.withDefaults())
            );
        }
        return http.build();
    }

    private boolean hasAnyOAuthProperty() {
        return hasOAuthJwtProperty() || hasOAuthOpaqueTokenProperty();
    }

    /**
     * @return true if one of the means to get the public JWK has been set in the application properties
     */
    private boolean hasOAuthJwtProperty() {
        var jwtProperties = oAuth2ResourceServerProperties.getJwt();
        return StringUtils.isNotEmpty(jwtProperties.getIssuerUri())
                || StringUtils.isNotEmpty(jwtProperties.getJwkSetUri())
                || jwtProperties.getPublicKeyLocation() != null;
    }

    /**
     * @return true if all the necessary settings have been provided to use opaque OAuth2.0 tokens
     */
    private boolean hasOAuthOpaqueTokenProperty() {
        var opaqueProperties = oAuth2ResourceServerProperties.getOpaquetoken();
        return StringUtils.isNotEmpty(opaqueProperties.getIntrospectionUri())
                && StringUtils.isNotEmpty(opaqueProperties.getClientId())
                && StringUtils.isNotEmpty(opaqueProperties.getClientSecret());
    }

    private static HttpSecurity allowAccessTo(HttpSecurity http, String... paths) throws Exception {
        return http
                // Apply security settings to API endpoints, Swagger UI, API documentation and actuator endpoints
                .securityMatchers(matchers -> matchers.requestMatchers(paths))
                // Disable CSRF protection since this is a stateless API (no browser sessions)
                .csrf(AbstractHttpConfigurer::disable)
                // Define authorization rules for different endpoints
                .authorizeHttpRequests(authorize -> authorize
                        // Allow public access to all endpoints as auth is handled via filter
                        .anyRequest().permitAll()
                );

    }
}
