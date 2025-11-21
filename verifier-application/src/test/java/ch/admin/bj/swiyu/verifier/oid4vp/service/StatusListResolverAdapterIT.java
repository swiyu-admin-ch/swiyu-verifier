/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.CachingConfig;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListFetchFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.STATUS_LIST_CACHE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest({StatusListResolverAdapter.class, CachingConfig.class})
@Import({StatusListResolverAdapterTestConfig.class})
class StatusListResolverAdapterIT {

    private final String url = "https://example.com/statuslist";
    @Autowired
    MockRestServiceServer mockServer;
    @Autowired
    StatusListResolverAdapter statusListResolverAdapter;
    @Autowired
    CacheManager cacheManager;

    @MockitoBean
    private UrlRewriteProperties urlRewriteProperties;
    @MockitoBean
    private ApplicationProperties applicationProperties;


    @BeforeEach
    void setUp() {
        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(url);
        cacheManager.getCache(STATUS_LIST_CACHE).clear();
    }

    @Test
    void testValidateStatusListSize_ExceedsMaxSize() {

        // Check with content size of 10 MB
        this.mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("Status list content", MediaType.TEXT_PLAIN)
                        .header("Content-Length", String.valueOf(10485760L)));

        var exception = assertThrows(StatusListMaxSizeExceededException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list size from " + url + " exceeds maximum allowed size", exception.getMessage());
    }

    @Test
    void testUnresolvableStatusList_thenStatusListException() {
        this.mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound().header("Content-Length", String.valueOf(100L)));

        var exception = assertThrows(StatusListFetchFailedException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list with uri: " + url + " could not be retrieved", exception.getMessage());
    }

    @Test
    void testChunkedStatusList_thenIllegalArgumentException() {
        this.mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess().header("Content-Length", String.valueOf(100L)).header("Transfer-Encoding", "chunked"));

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list size from " + url + " could not be determined (chunked transfer encoding)", exception.getMessage());
    }

    @Test
    void testUnresolvableStatusList_thenStatusListMaxSizeExceededException() {
        this.mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list size from " + url + " could not be determined", exception.getMessage());
    }

    @Test
    void testInvalidDomain_thenIllegalArgumentException() {
        var hosts = List.of("not_example.com");
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(hosts);

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("StatusList %s does not contain a valid host from %s".formatted(url, hosts), exception.getMessage());
    }

    @Test
    void testCorrectRewrittenUrlUsed_thenStatusListMaxSizeExceededException() {

        var differentUrl = "https://example-different.com/different";
        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(differentUrl);

        this.mockServer.expect(requestTo(differentUrl)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("statuslist", MediaType.TEXT_PLAIN).header("Content-Length", String.valueOf(100L)));
        statusListResolverAdapter.resolveStatusList(url);
        this.mockServer.verify();
    }


    @Test
    void testStatusListCaching_thenSuccess() {

        var expectedCacheValue = "statusList";
        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(url);

        this.mockServer.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(expectedCacheValue, MediaType.TEXT_PLAIN).header("Content-Length", String.valueOf(100L)));
        statusListResolverAdapter.resolveStatusList(url);

        assertEquals(expectedCacheValue, cacheManager.getCache(STATUS_LIST_CACHE).get(url).get());

        this.mockServer.verify();
    }

    @Test
    void testStatusIfCachingUsed_thenSuccess() {

        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(url);

        this.mockServer.expect(ExpectedCount.once(), requestTo(url)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("statusList", MediaType.TEXT_PLAIN).header("Content-Length", String.valueOf(100L)));

        statusListResolverAdapter.resolveStatusList(url);

        statusListResolverAdapter.resolveStatusList(url);

        this.mockServer.verify();
    }
}