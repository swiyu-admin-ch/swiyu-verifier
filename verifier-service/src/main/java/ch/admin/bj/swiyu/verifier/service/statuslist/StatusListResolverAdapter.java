/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.statuslist;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.MalformedURLException;
import java.net.URI;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.STATUS_LIST_CACHE;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Service
@Data
@Slf4j
@AllArgsConstructor
public class StatusListResolverAdapter {

    private final UrlRewriteProperties urlRewriteProperties;
    private final RestClient statusListRestClient;
    private final ApplicationProperties applicationProperties;
    private final CacheProperties cacheProperties;

    @Cacheable(value = STATUS_LIST_CACHE, condition = "@cacheProperties.statusListCacheTtl > 0L")
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
                        .formatted(rewrittenUrl, applicationProperties.getAcceptedStatusListHosts()));
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed URL %s in StatusList".formatted(rewrittenUrl), e);
        }


        return statusListRestClient.get()
                .uri(rewrittenUrl)
                .retrieve()
                .onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    throw new StatusListFetchFailedException("Status list with uri: %s could not be retrieved"
                            .formatted(rewrittenUrl));
                })
                .body(String.class);
    }
    
    private boolean isHttpsUrl(String url) {
        return url.startsWith("https://");
    }

    private boolean containsValidHost(String rewrittenUrl) throws MalformedURLException {

        var acceptedStatusListHosts = applicationProperties.getAcceptedStatusListHosts();
        var url = URI.create(rewrittenUrl).toURL();

        if (isEmpty(acceptedStatusListHosts)) {
            return true;
        }

        return applicationProperties.getAcceptedStatusListHosts().contains(url.getHost());
    }
}