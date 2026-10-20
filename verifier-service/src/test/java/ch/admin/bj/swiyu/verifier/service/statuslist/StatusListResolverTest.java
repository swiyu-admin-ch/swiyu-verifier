package ch.admin.bj.swiyu.verifier.service.statuslist;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatusListResolverTest {

    private UrlRewriteProperties urlRewriteProperties;
    private ApplicationProperties applicationProperties;
    private StatusListResolver statusListResolver;

    @BeforeEach
    void setUp() {
        urlRewriteProperties = mock(UrlRewriteProperties.class);
        WebClient webClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        applicationProperties = mock(ApplicationProperties.class);
        statusListResolver = new StatusListResolver(urlRewriteProperties, webClient, applicationProperties);
    }
    
    @Test
    void resolveStatusListWithInvalidHost_throwsException() {
        String uri = "https://example.com/statuslist";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedRegistryHosts()).thenReturn(List.of("other.com"));

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolver.resolveStatusList(uri));
        assertTrue(exception.getMessage().contains("does not contain a valid host"));
    }

    @Test
    void resolveStatusListWithoutHTTPS_throwsException() {
        String uri = "http://bad_url";
        when(urlRewriteProperties.getRewrittenUrl(uri)).thenReturn(uri);
        when(applicationProperties.getAcceptedRegistryHosts()).thenReturn(List.of());

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolver.resolveStatusList(uri));
        assertTrue(exception.getMessage().contains("does not use HTTPS"));
    }
}