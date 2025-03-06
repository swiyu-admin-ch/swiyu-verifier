/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.DidResolverException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Data
@AllArgsConstructor
public class StatusListResolverAdapter {

    private final UrlRewriteProperties urlRewriteProperties;
    private final RestClient statusListRestClient;

    public String resolveStatusList(String uri) {

        var rewrittenUrl = urlRewriteProperties.getRewrittenUrl(uri);

        return statusListRestClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    throw new DidResolverException( "Status list with uri: %s could not be retrieved".formatted(rewrittenUrl));
                })
                .body(String.class);
    }
}