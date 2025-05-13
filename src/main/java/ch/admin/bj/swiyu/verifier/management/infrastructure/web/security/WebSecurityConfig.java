/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.infrastructure.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Apply security settings to API endpoints, Swagger UI, API documentation and actuator endpoints
            .securityMatchers(matchers -> matchers.requestMatchers("/api/**", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**"))
            // Disable CSRF protection since this is a stateless API (no browser sessions)
            .csrf(AbstractHttpConfigurer::disable)
            // Define authorization rules for different endpoints
            .authorizeHttpRequests(authorize -> authorize
                    // Allow public access to all endpoints as auth is handled via filter
                    .anyRequest().permitAll()
            );

        return http.build();
    }
}
