/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.infrastructure.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import lombok.AllArgsConstructor;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AllArgsConstructor
@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI openApi() {
                return new OpenAPI().info(new io.swagger.v3.oas.models.info.Info()
                        .title("Verifier management API")
                        .description("Generic Verifier management service")
                        .contact(new io.swagger.v3.oas.models.info.Contact()
                                .name("e-ID - Team Tergum")
                                .email("eid@bit.admin.ch")
                        )
                );

        }

        @Bean
        GroupedOpenApi api() {
                return GroupedOpenApi.builder()
                        .group("API")
                        .pathsToMatch("/**")
                        .build();
        }
}
