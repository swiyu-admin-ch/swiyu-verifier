/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.DidResolverException;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Data
public class StatusListResolverAdapter {

    private final UrlRewriteProperties urlRewriteProperties;
    private final RestClient.Builder restClientBuilder;

    public StatusListResolverAdapter(UrlRewriteProperties urlRewriteProperties, RestClient.Builder builder) {
        this.urlRewriteProperties = urlRewriteProperties;
        this.restClientBuilder = builder;
    }

    public String resolveStatusList(String uri) {
        var restClient = restClientBuilder
                    .baseUrl(uri)
                    .requestInterceptor(new ContentLengthInterceptor())
                    .build();

        return restClient.get().retrieve()
                .onStatus(status -> status != HttpStatus.OK, (request, response) -> {
                    throw new DidResolverException( "Status list could not be retrieved");
                })
                .body(String.class);
    }
}
