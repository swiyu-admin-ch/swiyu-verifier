package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListFetchFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
        adapter = new StatusListResolverAdapter(urlRewriteProperties, restClient, applicationProperties);
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
    void resolveStatusList_invalidHost_throws() {
        String uri = "https://example.com/statuslist";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(List.of("other.com"));

        var exception = assertThrows(IllegalArgumentException.class, () -> adapter.resolveStatusList(uri));
        assertTrue(exception.getMessage().contains("does not contain a valid host"));
    }

    @Test
    void resolveStatusList_malformedUrl_throws() {
        String uri = "htt://bad_url";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(List.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> adapter.resolveStatusList(uri));
        assertTrue(exception.getMessage().contains("Malformed URL"));
    }

    @Test
    void resolveStatusList_nonOkStatus_throws() {
        String uri = "https://example.com/statuslist";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(List.of("example.com"));

        var retrieve = mock(RestClient.ResponseSpec.class);
        when(restClient.get().uri(uri).retrieve()).thenReturn(retrieve);
        // Simulate onStatus throwing
        when(retrieve.onStatus(any(), any())).thenAnswer(invocation -> {
            var predicate = invocation.getArgument(0);
            var handler = invocation.getArgument(1);
            if ((Boolean) predicate.getClass().cast(predicate).equals((HttpStatus.BAD_REQUEST != HttpStatus.OK))) {
                handler.getClass(); // just to use handler
            }
            throw new StatusListFetchFailedException("Status list with uri: %s could not be retrieved".formatted(uri));
        });

        assertThrows(StatusListFetchFailedException.class, () -> adapter.resolveStatusList(uri));
    }
}