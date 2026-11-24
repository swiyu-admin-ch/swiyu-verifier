package ch.admin.bj.swiyu.verifier.service.statuslist;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatusListResolverTest {

    private static final String STATUS_LIST_BODY = "{\"status_list\":{}}";

    private UrlRewriteProperties urlRewriteProperties;
    private ApplicationProperties applicationProperties;

    @BeforeEach
    void setUp() {
        urlRewriteProperties = mock(UrlRewriteProperties.class);
        applicationProperties = mock(ApplicationProperties.class);
    }

    @Test
    void resolveStatusList_withInvalidHost_throwsException() {
        String uri = "https://example.com/statuslist";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedRegistryHosts()).thenReturn(List.of("other.com"));

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolver().resolveStatusList(uri));
        assertTrue(exception.getMessage().contains("does not contain a valid host"));
    }

    @Test
    void resolveStatusList_withoutHttps_throwsException() {
        String uri = "http://bad_url";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedRegistryHosts()).thenReturn(List.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolver().resolveStatusList(uri));
        assertTrue(exception.getMessage().contains("does not use HTTPS"));
    }

    @Test
    void resolveStatusList_withHttpOriginalRewrittenToHttps_throwsException() {
        String originalUrl = "http://registry.example/statuslist";
        String rewrittenUrl = "https://internal.gateway.example/statuslist";
        when(urlRewriteProperties.getRewrittenUrl(originalUrl)).thenReturn(rewrittenUrl);

        var exception = assertThrows(IllegalArgumentException.class,
                () -> statusListResolver().resolveStatusList(originalUrl));

        assertTrue(exception.getMessage().contains("does not use HTTPS"));
        assertTrue(exception.getMessage().contains(originalUrl));
    }

    @Test
    void resolveStatusList_withHttpsOriginalRewrittenToHttp_throwsException() {
        String originalUrl = "https://registry.example/statuslist";
        String rewrittenUrl = "http://internal.gateway.example/statuslist";
        when(urlRewriteProperties.getRewrittenUrl(originalUrl)).thenReturn(rewrittenUrl);

        var exception = assertThrows(IllegalArgumentException.class,
                () -> statusListResolver().resolveStatusList(originalUrl));

        assertTrue(exception.getMessage().contains("is rewritten to a non-HTTPS URL"));
        assertTrue(exception.getMessage().contains(rewrittenUrl));
    }

    @Test
    void resolveStatusList_withHttpsOriginalRewrittenToHttps_retrievesRewrittenUrl() {
        String originalUrl = "https://registry.example/statuslist";
        String rewrittenUrl = "https://internal.gateway.example/statuslist";

        String result = resolveWithRecordingWebClient(originalUrl, rewrittenUrl);

        assertEquals(STATUS_LIST_BODY, result);
    }

    private String resolveWithRecordingWebClient(String originalUrl, String rewrittenUrl) {
        when(urlRewriteProperties.getRewrittenUrl(originalUrl)).thenReturn(rewrittenUrl);
        when(applicationProperties.getAcceptedRegistryHosts()).thenReturn(List.of());

        var requestedUrl = new AtomicReference<String>();
        ExchangeFunction exchangeFunction = request -> {
            requestedUrl.set(request.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(STATUS_LIST_BODY)
                    .build());
        };

        String result = statusListResolver(WebClient.builder().exchangeFunction(exchangeFunction).build())
                .resolveStatusList(originalUrl);

        assertEquals(rewrittenUrl, requestedUrl.get());
        return result;
    }

    private StatusListResolver statusListResolver() {
        return statusListResolver(mock(WebClient.class));
    }

    private StatusListResolver statusListResolver(WebClient statusListWebClient) {
        return new StatusListResolver(urlRewriteProperties, statusListWebClient, applicationProperties);
    }
}
