/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

@Service
@AllArgsConstructor
@Data
public class StatusListResolverAdapter {

    private static final int MAX_STATUS_LIST_SIZE = 10485760; // 10 MB
    private final UrlRewriteProperties urlRewriteProperties;
    private final RestClient.Builder restClientBuilder;

    public String resolveStatusList(String uri) {
        try {
            var rewrittenUrl = urlRewriteProperties.getRewrittenUrl(uri);
            validateStatusListSize(URI.create(rewrittenUrl).toURL());
            return restClientBuilder.build()
                    .get()
                    .uri(rewrittenUrl)
                    .retrieve()
                    .body(String.class);

        } catch (MalformedURLException e) {
            throw VerificationException.credentialError(VerificationErrorResponseCode.CREDENTIAL_INVALID, "Invalid URI: " + uri);
        } catch (IllegalArgumentException e) {
            throw VerificationException.credentialError(VerificationErrorResponseCode.CREDENTIAL_INVALID, e.getMessage());
        }
    }

    void validateStatusListSize(URL url) {
        try {
            var connection = url.openConnection();
            var contentLength = connection.getContentLengthLong();

            if (contentLength > MAX_STATUS_LIST_SIZE) {
                throw new IllegalArgumentException("Status list size from " + url + " exceeds maximum allowed size");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to validate status list size from " + url, e);
        }
    }
}
