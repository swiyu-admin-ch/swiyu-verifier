package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.infrastructure.config.ContentLengthInterceptor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@TestConfiguration
public class StatusListResolverAdapterTestConfig {

    @Bean
    public RestClient defaultRestClient(RestClient.Builder builder) {
        return builder
                .requestInterceptor(new ContentLengthInterceptor(1000))
                .build();
    }
}