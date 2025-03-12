/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.StatusListFetchFailedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.MalformedURLException;
import java.net.URI;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Service
@Data
@AllArgsConstructor
public class StatusListResolverAdapter {

    private final UrlRewriteProperties urlRewriteProperties;
    private final RestClient statusListRestClient;
    private final ApplicationProperties applicationProperties;

    public String resolveStatusList(String uri) {

        var rewrittenUrl = urlRewriteProperties.getRewrittenUrl(uri);

        try {
            if (!containsValidHost(rewrittenUrl)) {
                throw new IllegalArgumentException("StatusList %s does not contain a valid host from %s"
                        .formatted(rewrittenUrl, applicationProperties.getAcceptedStatusListHosts()));
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed URL %s in StatusList".formatted(rewrittenUrl), e);
        }


        return statusListRestClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    throw new StatusListFetchFailedException("Status list with uri: %s could not be retrieved"
                            .formatted(rewrittenUrl));
                })
                .body(String.class);
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