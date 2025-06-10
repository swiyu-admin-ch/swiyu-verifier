package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("webhook")
public class WebhookProperties {
    @Nullable
    private String callbackUri;
    @Nullable
    private String apiKeyHeader;
    @Nullable
    private String apiKeyValue;
}