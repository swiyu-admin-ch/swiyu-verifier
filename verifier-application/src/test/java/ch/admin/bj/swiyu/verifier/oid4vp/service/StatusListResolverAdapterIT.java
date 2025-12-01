/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.CachingConfig;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.infrastructure.config.RestClientConfig;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListFetchFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mockserver.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.STATUS_LIST_CACHE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Testcontainers
@RestClientTest({StatusListResolverAdapter.class, CachingConfig.class})
@Import({RestClientConfig.class, StatusListResolverAdapterIT.TestConfig.class, VerificationProperties.class})
@TestPropertySource(properties = "verification.object-size-limit=10")
class StatusListResolverAdapterIT {

    @TestConfiguration
    static class TestConfig {
        @Bean
        VerificationProperties verificationProperties() {
            var mimi = new VerificationProperties();
            mimi.setObjectSizeLimit(10);
            return mimi;
        }
    }

    private final String url = "https://example.com/statuslist";
    private String rewrittenUrl;

    @Autowired
    WebClient statusListWebClient;
    @Autowired
    StatusListResolverAdapter statusListResolverAdapter;
    @Autowired
    CacheManager cacheManager;

    // mocked collaborators provided to the slice test
    @MockitoBean
    private UrlRewriteProperties urlRewriteProperties;

    @Autowired
    private VerificationProperties verificationProperties;

    @MockitoBean
    private ApplicationProperties applicationProperties;

    // Start MockServerContainer statically so it is available before Spring context initialization
    @Container
    private static final MockServerContainer mockServerContainer =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:latest"));

    private MockServerClient mockServerClient;

    @BeforeEach
    void setUp() {

        cacheManager.getCache(STATUS_LIST_CACHE).clear();

        mockServerClient = new MockServerClient(mockServerContainer.getHost(), mockServerContainer.getServerPort());

        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(url);

        mockServerClient.clear(request().withMethod("GET").withPath("/statuslist"));

        rewrittenUrl = "http://" + mockServerContainer.getHost() + ":" + mockServerContainer.getServerPort() + "/statuslist";

        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(rewrittenUrl);
    }

    @Test
    void testValidateStatusListSize_ExceedsMaxSize() {

        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn("http://" + mockServerContainer.getHost() + ":" + mockServerContainer.getServerPort() + "/statuslist");

        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                        .withBody("Status list content sadjajdhjkashdjkhasjdkhasjdhjashdjaskdhasjdhasjhdajskhdjashdjhasdjhasjkdhashdjh"));

        var exception = assertThrows(StatusListMaxSizeExceededException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list size from " + rewrittenUrl + " exceeds maximum allowed size", exception.getMessage());

        // ensure request was received
        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.atLeast(1));
    }


    @Test
    void testUnresolvableStatusList_thenStatusListMaxSizeExceededException() {
        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response().withStatusCode(404));

        var exception = assertThrows(StatusListFetchFailedException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("Status list with uri: " + rewrittenUrl + " could not be retrieved", exception.getMessage());

        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.atLeast(1));
    }

    @Test
    void testInvalidDomain_thenIllegalArgumentException() {
        var hosts = List.of("not_example.com");
        when(applicationProperties.getAcceptedStatusListHosts()).thenReturn(hosts);

        // For this test we want the rewritten URL to be the original URL so that the domain check runs
        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(url);

        var exception = assertThrows(IllegalArgumentException.class, () -> statusListResolverAdapter.resolveStatusList(url));
        assertEquals("StatusList %s does not contain a valid host from %s".formatted(url, hosts), exception.getMessage());
    }

    @Test
    void testStatusListCaching_thenSuccess() {

        var expectedCacheValue = "statusList";
        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn("http://" + mockServerContainer.getHost() + ":" + mockServerContainer.getServerPort() + "/statuslist");

        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response().withStatusCode(200).withBody(expectedCacheValue).withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE));

        statusListResolverAdapter.resolveStatusList(url);

        assertEquals(expectedCacheValue, cacheManager.getCache(STATUS_LIST_CACHE).get(url).get());

        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.exactly(1));
    }

    @Test
    void testStatusIfCachingUsed_thenSuccess() {

        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn("http://" + mockServerContainer.getHost() + ":" + mockServerContainer.getServerPort() + "/statuslist");

        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response().withStatusCode(200).withBody("1").withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE));

        statusListResolverAdapter.resolveStatusList(url);

        statusListResolverAdapter.resolveStatusList(url);

        // only one network request should have been made due to caching
        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.exactly(1));
    }
}