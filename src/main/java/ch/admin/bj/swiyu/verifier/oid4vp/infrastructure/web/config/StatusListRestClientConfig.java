/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@AllArgsConstructor
public class StatusListRestClientConfig {

    @Bean
    public RestClient statusListRestClient(RestClient.Builder builder) {
        return builder
                .requestInterceptor(new ContentLengthInterceptor())
                .build();
    }
}