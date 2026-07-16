package ch.admin.bj.swiyu.verifier.service.statuslist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;

class StatusListCacheServiceTest {


    StatusListCacheService cacheService;
    CacheProperties cacheProperties;
    DidJwtValidator didJwtValidator;
    IssuerPublicKeyLoader issuerPublicKeyLoader;
    StatusListResolver statusListResolver;

    @BeforeEach
    void setup() {
        cacheProperties = new CacheProperties();
        cacheProperties.setStatusListCacheSize(100);
        cacheProperties.setRequestBackoffSeconds(100);
        cacheProperties.setStatusListCacheTtl(500l);
        didJwtValidator = mock(DidJwtValidator.class);
        issuerPublicKeyLoader = mock(IssuerPublicKeyLoader.class);
        statusListResolver = mock(StatusListResolver.class);
    }

    /**
     * Test mocking a valid Token Status List resolution
     */
    @Test
    void testGetTokenStatusListTokenByUri() throws Exception {
        cacheService = new StatusListCacheService(cacheProperties, didJwtValidator, issuerPublicKeyLoader, statusListResolver);
        ECKey testKey = new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyID("did:webvh:example.com#key-1")
            .keyUse(KeyUse.SIGNATURE)
            .generate();
        when(issuerPublicKeyLoader.loadJWK(any(), eq(testKey.getKeyID()))).thenReturn(testKey.toPublicJWK());
        var statusListJwt = StatusListGenerator.createTokenStatusListTokenVerifiableCredential(StatusListGenerator.SPEC_STATUS_LIST, testKey, "did:example", testKey.getKeyID());

        when(statusListResolver.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT))).thenReturn(statusListJwt);

        var statusList = assertDoesNotThrow(() -> cacheService.getTokenStatusListTokenByUri(StatusListGenerator.SPEC_SUBJECT));
        verify(didJwtValidator, times(1)).validateJwt(eq(statusListJwt), any(JWKSet.class));
        assertThat(statusList).isNotNull();
        assertThat(statusList.getStatusList()).isNotNull();
        assertThat(statusList.getExp()).isNotNull().isNotZero();
        assertThat(statusList.getTtl()).isNotNull().isNotZero();
        assertThat(cacheService.getCache().estimatedSize()).isEqualTo(1);
    }


    /**
     * Test mocking a valid Token Status List resolution
     */
    @Test
    void testGetTokenStatusListTokenByUri_noCache() throws Exception {
        cacheProperties.setStatusListCacheTtl(0l);
        // Must create cache serivce here, as when initiated the TTL is set for the cache
        cacheService = new StatusListCacheService(cacheProperties, didJwtValidator, issuerPublicKeyLoader, statusListResolver);
        ECKey testKey = new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyID("did:webvh:example.com#key-1")
            .keyUse(KeyUse.SIGNATURE)
            .generate();
        when(issuerPublicKeyLoader.loadJWK(any(), eq(testKey.getKeyID()))).thenReturn(testKey.toPublicJWK());
        var statusListJwt = StatusListGenerator.createTokenStatusListTokenVerifiableCredential(StatusListGenerator.SPEC_STATUS_LIST, testKey, "did:example", testKey.getKeyID());

        when(statusListResolver.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT))).thenReturn(statusListJwt);

        var statusList = assertDoesNotThrow(() -> cacheService.getTokenStatusListTokenByUri(StatusListGenerator.SPEC_SUBJECT));
        verify(didJwtValidator, times(1)).validateJwt(eq(statusListJwt), any(JWKSet.class));
        assertThat(statusList).isNotNull();
        assertThat(statusList.getStatusList()).isNotNull();
        assertThat(statusList.getExp()).isNotNull().isNotZero();
        assertThat(statusList.getTtl()).isNotNull().isNotZero();
        assertThat(cacheService.getCache().estimatedSize()).as("Expiry time was 0, thus will not be cached").isEqualTo(0);
    }
}
