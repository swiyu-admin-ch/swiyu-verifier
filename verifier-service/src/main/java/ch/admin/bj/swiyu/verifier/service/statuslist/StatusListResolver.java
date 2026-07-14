package ch.admin.bj.swiyu.verifier.service.statuslist;

import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URI;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

/**
 * Resolves Status Lists and caches them if possible
 */
@Service
@Data
@Slf4j
@RequiredArgsConstructor
public class StatusListResolver {

    private final UrlRewriteProperties urlRewriteProperties;
    private final WebClient statusListWebClient;
    private final ApplicationProperties applicationProperties;

    public String resolveStatusList(String uri) {

        var rewrittenUrl = urlRewriteProperties.getRewrittenUrl(uri);
        log.debug("HTTP Request after url rewrite to status list from {}", rewrittenUrl);
        try {
            // check if https request otherwise throw exception
            if (!isHttpsUrl(uri)) {
                throw new IllegalArgumentException("StatusList %s does not use HTTPS"
                        .formatted(uri));
            }

            if (!containsValidHost(rewrittenUrl)) {
                throw new IllegalArgumentException("StatusList %s does not contain a valid host from %s"
                        .formatted(rewrittenUrl, applicationProperties.getAcceptedRegistryHosts()));
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed URL %s in StatusList".formatted(rewrittenUrl), e);
        }

        var result = statusListWebClient
                .get()
                .uri(rewrittenUrl)
                .retrieve()
                .onStatus(status -> status != HttpStatusCode.valueOf(200), response ->
                        Mono.error(new StatusListFetchFailedException(
                                "Status list with uri: %s could not be retrieved".formatted(rewrittenUrl))))
                .bodyToMono(String.class)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getCause().toString().contains("DataBufferLimitException")) {
                        return Mono.error(new StatusListMaxSizeExceededException(
                                "Status list size from %s exceeds maximum allowed size".formatted(rewrittenUrl)));
                    }
                    log.error("Error while fetching status list from {}: {}", rewrittenUrl, ex.getMessage());
                    return Mono.error(new StatusListFetchFailedException(
                            "Status list with uri: %s could not be retrieved".formatted(rewrittenUrl)));
                })
                .block();

        if (result == null) {
            throw new StatusListFetchFailedException(
                    "Status list with uri: %s returned an empty response".formatted(rewrittenUrl));
        }
        return result;
    }

    private boolean isHttpsUrl(String url) {
        return url.startsWith("https://");
    }

    private boolean containsValidHost(String rewrittenUrl) throws MalformedURLException {

        var acceptedStatusListHosts = applicationProperties.getAcceptedRegistryHosts();
        var url = URI.create(rewrittenUrl).toURL();

        if (isEmpty(acceptedStatusListHosts)) {
            return true;
        }

        return applicationProperties.getAcceptedRegistryHosts().contains(url.getHost());
    }
}