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
import org.springframework.web.reactive.function.client.WebClient;

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

    // used to fetch status lists with max memory size limit
    @Bean
    public WebClient defaultWebClient(WebClient.Builder builder) {
        return builder
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(verificationProperties.getObjectSizeLimit()))
                .build();
    }
}