/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.infrastructure.config;

import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@AllArgsConstructor
public class RestClientConfig {
    private final VerificationProperties verificationProperties;

    @Bean
    public RestClient defaultRestClient(RestClient.Builder builder) {
        return builder
                .requestInterceptor(new ContentLengthInterceptor(verificationProperties.getObjectSizeLimit()))
                .build();
    }
}