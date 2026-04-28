package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.config.*;
import ch.admin.bj.swiyu.verifier.infrastructure.config.WebClientConfig;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListFetchFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mockserver.MockServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.STATUS_LIST_CACHE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {StatusListResolverAdapter.class, CachingConfig.class, CacheProperties.class})
@Import({WebClientConfig.class, StatusListResolverAdapterIT.TestConfig.class, VerificationProperties.class, StatusListCache.class})
@TestPropertySource(properties = {
        "verification.object-size-limit=10",
        "caching.status-list-cache-ttl=250",
        "caching.issuer-public-key-cache-ttl=1000"
})
class StatusListResolverAdapterIT {

    @TestConfiguration
    @EnableScheduling
    static class TestConfig {
        @Bean
        VerificationProperties verificationProperties() {
            var verificationProps = new VerificationProperties();
            verificationProps.setObjectSizeLimit(10);
            return verificationProps;
        }
    }

    private static final Duration CACHE_EVICTION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration CACHE_EVICTION_POLL_INTERVAL = Duration.ofMillis(25);

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
    @MockitoBean
    private CacheProperties cacheProperties;

    // Start MockServerContainer statically so it is available before Spring context initialization
    @Container
    private static final MockServerContainer mockServerContainer =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:latest"));

    private MockServerClient mockServerClient;

    @BeforeEach
    void setUp() {

        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(url);
        when(cacheProperties.getStatusListCacheTtl()).thenReturn(0L);

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

        when(cacheProperties.getStatusListCacheTtl()).thenReturn(1000L);
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

        when(cacheProperties.getStatusListCacheTtl()).thenReturn(1000L);

        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn(url);
        when(urlRewriteProperties.getRewrittenUrl(url)).thenReturn("http://" + mockServerContainer.getHost() + ":" + mockServerContainer.getServerPort() + "/statuslist");

        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response().withStatusCode(200).withBody("1").withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE));

        statusListResolverAdapter.resolveStatusList(url);

        statusListResolverAdapter.resolveStatusList(url);

        // only one network request should have been made due to caching
        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.exactly(1));
    }

    @Test
    void testStatusListCaching_whenCachingDisabled_thenAlwaysFetchesFromRemote() {
        // caching is disabled when ttl = 0
        when(cacheProperties.getStatusListCacheTtl()).thenReturn(0L);

        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response().withStatusCode(200).withBody("statusList").withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE));

        statusListResolverAdapter.resolveStatusList(url);
        statusListResolverAdapter.resolveStatusList(url);

        // both calls must have hit the remote – no caching
        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.exactly(2));
        assertNull(cacheManager.getCache(STATUS_LIST_CACHE).get(url));
    }

    @Test
    void testStatusListCaching_whenCacheEvicted_thenFetchesFromRemoteAgain() {
        when(cacheProperties.getStatusListCacheTtl()).thenReturn(1000L);

        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response().withStatusCode(200).withBody("statusList").withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE));

        // first call – populates the cache
        statusListResolverAdapter.resolveStatusList(url);
        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.exactly(1));

        // evict the cache
        cacheManager.getCache(STATUS_LIST_CACHE).clear();

        // second call – cache is empty, must fetch again
        statusListResolverAdapter.resolveStatusList(url);
        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.exactly(2));
    }

    @Test
    void testStatusListCaching_differentUris_areCachedSeparately() {
        when(cacheProperties.getStatusListCacheTtl()).thenReturn(1000L);

        var url2 = "https://example.com/statuslist2";
        var rewrittenUrl2 = "http://" + mockServerContainer.getHost() + ":" + mockServerContainer.getServerPort() + "/statuslist2";
        when(urlRewriteProperties.getRewrittenUrl(url2)).thenReturn(rewrittenUrl2);

        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response().withStatusCode(200).withBody("list1").withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE));
        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist2"))
                .respond(response().withStatusCode(200).withBody("list2").withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE));

        var result1 = statusListResolverAdapter.resolveStatusList(url);
        var result2 = statusListResolverAdapter.resolveStatusList(url2);

        assertEquals("list1", result1);
        assertEquals("list2", result2);

        // each URI must have its own cache entry
        assertEquals("list1", cacheManager.getCache(STATUS_LIST_CACHE).get(url).get());
        assertEquals("list2", cacheManager.getCache(STATUS_LIST_CACHE).get(url2).get());

        // each remote was called exactly once
        mockServerClient.verify(request().withPath("/statuslist"), VerificationTimes.exactly(1));
        mockServerClient.verify(request().withPath("/statuslist2"), VerificationTimes.exactly(1));
    }

    /**
     * Integration Test: Verifies that the scheduled cache eviction mechanism removes cached status list entries as expected.
     * <p>
     * What is tested: The cache entry for a status list URI is evicted by the scheduled task after the configured TTL.
     * Why: Ensures that the cache does not retain stale entries and that the eviction logic works reliably in a real application context.
     * Boundary conditions: The cache is populated with a single entry, and the TTL is set to a high value to allow the scheduled eviction to run.
     * Expected output: The cache entry is removed within the timeout window, and subsequent access returns null.
     */
    @Test
    void testStatusListCaching_whenScheduledEvictionRuns_thenCacheEntryIsRemoved() throws InterruptedException {
        when(cacheProperties.getStatusListCacheTtl()).thenReturn(100000L);

        mockServerClient
                .when(request().withMethod("GET").withPath("/statuslist"))
                .respond(response().withStatusCode(200).withBody("statusList").withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE));

        statusListResolverAdapter.resolveStatusList(url);

        Cache statusListCache = cacheManager.getCache(STATUS_LIST_CACHE);
        assertNotNull(statusListCache);
        assertEquals("statusList", statusListCache.get(url).get());

        waitUntilCacheEntryIsEvicted(statusListCache, url);

        assertNull(statusListCache.get(url), "Scheduled cache eviction should remove the cached status list entry");
    }

    private void waitUntilCacheEntryIsEvicted(Cache cache, String key) throws InterruptedException {
        long timeoutAt = System.nanoTime() + CACHE_EVICTION_TIMEOUT.toNanos();

        while (System.nanoTime() < timeoutAt) {
            if (cache.get(key) == null) {
                return;
            }
            Thread.sleep(CACHE_EVICTION_POLL_INTERVAL.toMillis());
        }

        fail("Cache entry '%s' was not evicted within %s".formatted(key, CACHE_EVICTION_TIMEOUT));
    }

}

