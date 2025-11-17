/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.infrastructure.config;

import java.io.IOException;
import java.net.URI;

import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

@RequiredArgsConstructor
public class ContentLengthInterceptor implements ClientHttpRequestInterceptor {


    private final int maxSize;
    private static final String CHUNKED_TRANSFER_ENCODING = "chunked";

    @NotNull
    @Override
    public ClientHttpResponse intercept(@NotNull HttpRequest request, @NotNull byte[] body, ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        long contentLength = response.getHeaders().getContentLength();

        String transferEncoding = response.getHeaders().getFirst(HttpHeaders.TRANSFER_ENCODING);

        // decline if transfer-encoding is chunked
        if (CHUNKED_TRANSFER_ENCODING.equalsIgnoreCase(transferEncoding)) {
            throw new IllegalArgumentException(getStatusListSizeUnknownMessage(request.getURI()) + " (chunked transfer encoding)");
        }

        if (contentLength == -1) {
            throw new IllegalArgumentException(getStatusListSizeUnknownMessage(request.getURI()));
        }

        if (contentLength > this.maxSize) {
            throw new StatusListMaxSizeExceededException("Status list size from %s exceeds maximum allowed size".formatted(request.getURI()));
        }

        return response;
    }

    private String getStatusListSizeUnknownMessage(URI uri) {
        return "Status list size from %s could not be determined".formatted(uri);
    }
}