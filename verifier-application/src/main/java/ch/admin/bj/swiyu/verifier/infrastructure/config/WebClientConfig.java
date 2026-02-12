package ch.admin.bj.swiyu.verifier.infrastructure.config;

import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@AllArgsConstructor
public class WebClientConfig {
    private final VerificationProperties verificationProperties;

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