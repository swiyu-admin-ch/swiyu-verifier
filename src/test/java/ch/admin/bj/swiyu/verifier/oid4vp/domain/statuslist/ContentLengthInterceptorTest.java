/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.config.ContentLengthInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class ContentLengthInterceptorTest {

    private ContentLengthInterceptor contentLengthInterceptor;
    private HttpRequest request;
    private ClientHttpRequestExecution execution;
    private ClientHttpResponse response;

    @BeforeEach
    void setUp() {
        contentLengthInterceptor = new ContentLengthInterceptor(204800);
        request = mock(HttpRequest.class);
        execution = mock(ClientHttpRequestExecution.class);
        response = mock(ClientHttpResponse.class);
    }

    @Test
    void testIntercept_ContentLengthWithinLimit() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(1024L); // 1 KB
        when(response.getHeaders()).thenReturn(headers);
        when(execution.execute(Mockito.any(HttpRequest.class), Mockito.any(byte[].class))).thenReturn(response);

        assertDoesNotThrow(() -> contentLengthInterceptor.intercept(request, new byte[0], execution));
    }

    @Test
    void testIntercept_ContentLengthExceedsLimit() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(10485761L); // 10 MB + 1 byte
        when(response.getHeaders()).thenReturn(headers);
        when(execution.execute(Mockito.any(HttpRequest.class), Mockito.any(byte[].class))).thenReturn(response);

        assertThrows(IllegalArgumentException.class, () -> contentLengthInterceptor.intercept(request, new byte[0], execution));
    }
}