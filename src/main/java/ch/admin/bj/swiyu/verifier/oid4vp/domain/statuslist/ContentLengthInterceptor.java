/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class ContentLengthInterceptor implements ClientHttpRequestInterceptor {

    private static final int MAX_STATUS_LIST_SIZE = 10485760; // 10 MB

    @NotNull
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        long contentLength = response.getHeaders().getContentLength();

        if (contentLength > MAX_STATUS_LIST_SIZE) {
            throw new IllegalArgumentException("Status list size from %s exceeds maximum allowed size".formatted(request.getURI()));
        }

        return response;
    }
}
