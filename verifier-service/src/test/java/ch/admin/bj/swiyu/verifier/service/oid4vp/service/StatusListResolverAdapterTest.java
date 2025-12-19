package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListFetchFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatusListResolverAdapterTest {

    private UrlRewriteProperties urlRewriteProperties;
    private RestClient restClient;
    private WebClient webClient;
    private ApplicationProperties applicationProperties;
    private StatusListResolverAdapter adapter;

    @BeforeEach
    void setUp() {
        urlRewriteProperties = mock(UrlRewriteProperties.class);
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        webClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        applicationProperties = mock(ApplicationProperties.class);
        CacheProperties cacheProperties = mock(CacheProperties.class);
        adapter = new StatusListResolverAdapter(urlRewriteProperties, webClient, applicationProperties, cacheProperties);
    }
    
    @Test
    void resolveStatusListWithInvalidHost_throwsException() {
        String uri = "https://example.com/statuslist";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(List.of("other.com"));

        var exception = assertThrows(IllegalArgumentException.class, () -> adapter.resolveStatusList(uri));
        assertTrue(exception.getMessage().contains("does not contain a valid host"));
    }

    @Test
    void resolveStatusListWithoutHTTPS_throwsException() {
        String uri = "http://bad_url";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(List.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> adapter.resolveStatusList(uri));
        assertTrue(exception.getMessage().contains("does not use HTTPS"));
    }
}