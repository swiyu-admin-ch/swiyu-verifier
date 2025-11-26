package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListFetchFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatusListResolverAdapterTest {

    private UrlRewriteProperties urlRewriteProperties;
    private RestClient restClient;
    private ApplicationProperties applicationProperties;
    private StatusListResolverAdapter adapter;

    @BeforeEach
    void setUp() {
        urlRewriteProperties = mock(UrlRewriteProperties.class);
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        applicationProperties = mock(ApplicationProperties.class);
        CacheProperties cacheProperties = mock(CacheProperties.class);
        adapter = new StatusListResolverAdapter(urlRewriteProperties, restClient, applicationProperties, cacheProperties);
    }

    @Test
    void resolveStatusList_successfulFetch() {
        String uri = "https://example.com/statuslist";
        String expected = "status-list-content";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(List.of("example.com"));
        // Mock RestClient chain
        var retrieve = mock(RestClient.ResponseSpec.class);
        when(restClient.get().uri(uri).retrieve()).thenReturn(retrieve);
        when(retrieve.onStatus(any(), any())).thenReturn(retrieve);
        when(retrieve.body(String.class)).thenReturn(expected);

        String result = adapter.resolveStatusList(uri);
        assertEquals(expected, result);
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

    @Test
    void resolveStatusListWithNonOkStatus_throwsException() {
        String uri = "https://example.com/statuslist";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(List.of("example.com"));

        var retrieve = mock(RestClient.ResponseSpec.class);
        when(restClient.get().uri(uri).retrieve()).thenReturn(retrieve);
        // Simulate onStatus throwing
        when(retrieve.onStatus(any(), any())).thenAnswer(invocation -> {
            throw new StatusListFetchFailedException("Status list with uri: %s could not be retrieved".formatted(uri));
        });

        assertThrows(StatusListFetchFailedException.class, () -> adapter.resolveStatusList(uri));
    }
}