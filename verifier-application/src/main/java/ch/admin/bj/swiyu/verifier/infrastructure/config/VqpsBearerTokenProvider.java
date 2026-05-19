package ch.admin.bj.swiyu.verifier.infrastructure.config;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides a cached OAuth2 Bearer token for the TMS B2B Authoring API.
 *
 * <p>Fetches a new token via the client_credentials grant if the cached token
 * is absent or about to expire (within 30 seconds). Thread-safe via atomic references.</p>
 *
 * <p>Only active when {@code swiyu.trust-registry.tms-authoring-url} is configured.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${swiyu.trust-registry.tms-authoring-url:}'.length() > 0")
public class VqpsBearerTokenProvider {

    private static final int TOKEN_EXPIRY_BUFFER_SECONDS = 30;

    private final TrustRegistryProperties properties;
    private final WebClient webClient;

    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private final AtomicLong cachedTokenExpiry = new AtomicLong(0);

    /**
     * Returns a valid OAuth2 Bearer token, refreshing it if expired or about to expire.
     *
     * @return a non-null Bearer token string
     * @throws IllegalStateException if the token endpoint returns no token or is unreachable
     */
    @SuppressWarnings("unchecked")
    public String getToken() {
        long now = Instant.now().getEpochSecond();
        String existing = cachedToken.get();
        if (existing != null && cachedTokenExpiry.get() > now + TOKEN_EXPIRY_BUFFER_SECONDS) {
            return existing;
        }

        log.debug("Fetching new OAuth2 access token from {}", properties.getOauthTokenUrl());
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.getOauthClientId());
        form.add("client_secret", properties.getOauthClientSecret());

        Map<String, Object> tokenResponse = webClient.post()
                .uri(properties.getOauthTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain OAuth2 access token from " + properties.getOauthTokenUrl());
        }

        String token = (String) tokenResponse.get("access_token");
        int expiresIn = tokenResponse.containsKey("expires_in")
                ? ((Number) tokenResponse.get("expires_in")).intValue()
                : 300;

        cachedToken.set(token);
        cachedTokenExpiry.set(now + expiresIn);
        log.debug("OAuth2 access token obtained for TMS B2B API, expires in {}s", expiresIn);
        return token;
    }
}

