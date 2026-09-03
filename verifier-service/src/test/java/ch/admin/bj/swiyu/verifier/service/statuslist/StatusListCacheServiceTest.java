package ch.admin.bj.swiyu.verifier.service.statuslist;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StatusListCacheServiceTest {


    StatusListCacheService cacheService;
    CacheProperties cacheProperties;
    DidJwtValidator didJwtValidator;
    DidResolverFacade issuerPublicKeyLoader;
    StatusListResolver statusListResolver;

    @BeforeEach
    void setup() {
        cacheProperties = new CacheProperties();
        cacheProperties.setStatusListCacheSize(100);
        cacheProperties.setRequestBackoffSeconds(100);
        didJwtValidator = mock(DidJwtValidator.class);
        issuerPublicKeyLoader = mock(DidResolverFacade.class);
        statusListResolver = mock(StatusListResolver.class);
    }

    /**
     * Test mocking a valid Token Status List resolution
     */
    @Test
    void testGetTokenStatusListTokenByUri() throws Exception {
        cacheProperties.setStatusListCacheTtlMs(500L);
        cacheService = new StatusListCacheService(cacheProperties, didJwtValidator, issuerPublicKeyLoader, statusListResolver);
        ECKey testKey = createSigningKey();
        when(issuerPublicKeyLoader.resolveKey(eq(testKey.getKeyID()))).thenReturn(testKey.toPublicJWK());
        var statusListJwt = StatusListGenerator.createTokenStatusListTokenVerifiableCredential(StatusListGenerator.SPEC_STATUS_LIST, testKey, "did:example", testKey.getKeyID());
        when(statusListResolver.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT))).thenReturn(statusListJwt);

        var statusList = assertDoesNotThrow(() -> cacheService.getTokenStatusListTokenByUri(StatusListGenerator.SPEC_SUBJECT));

        verify(didJwtValidator, times(1)).validateJwt(eq(statusListJwt), any(JWK.class));
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
        cacheProperties.setStatusListCacheTtlMs(0L);
        // Must create cache serivce here, as when initiated the TTL is set for the cache
        cacheService = new StatusListCacheService(cacheProperties, didJwtValidator, issuerPublicKeyLoader, statusListResolver);
        ECKey testKey = createSigningKey();
        when(issuerPublicKeyLoader.resolveKey(eq(testKey.getKeyID()))).thenReturn(testKey.toPublicJWK());
        var statusListJwt = StatusListGenerator.createTokenStatusListTokenVerifiableCredential(StatusListGenerator.SPEC_STATUS_LIST, testKey, "did:example", testKey.getKeyID());

        when(statusListResolver.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT))).thenReturn(statusListJwt);

        var statusList = assertDoesNotThrow(() -> cacheService.getTokenStatusListTokenByUri(StatusListGenerator.SPEC_SUBJECT));
        // Ensure data is returned
        assertThat(statusList).isNotNull();
        assertThat(statusList.getStatusList()).isNotNull();
        assertThat(statusList.getExp()).isNotNull().isNotZero();
        assertThat(statusList.getTtl()).isNotNull().isNotZero();
        // Second Invocation to prove nothing is cached
        // Note: cache.getEstimatedSize() is flaky
        assertDoesNotThrow(() -> cacheService.getTokenStatusListTokenByUri(StatusListGenerator.SPEC_SUBJECT));
        verify(didJwtValidator, times(2)).validateJwt(eq(statusListJwt), any(JWK.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-statuslist+jwt"})
    void getTokenStatusListTokenByUri_resolveValidatedStatusList_withIncorrectHeader_returnsOptionalEmpty(String type) throws JOSEException {
        cacheService = new StatusListCacheService(cacheProperties, didJwtValidator, issuerPublicKeyLoader, statusListResolver);
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType(type))
                .build();

        // does not need to contain anything
        var claimSet = new JWTClaimsSet.Builder().build();
        var jwt = new SignedJWT(header, claimSet);
        jwt.sign(new ECDSASigner(createSigningKey()));

        when(statusListResolver.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT))).thenReturn(jwt.serialize());

        assertThrows(VerificationException.class, () -> cacheService.getTokenStatusListTokenByUri(StatusListGenerator.SPEC_SUBJECT));
    }

    private ECKey createSigningKey() {
        return assertDoesNotThrow(() -> new ECKeyGenerator(Curve.P_256)
                .algorithm(JWSAlgorithm.ES256)
                .keyID("did:webvh:example.com#key-1")
                .keyUse(KeyUse.SIGNATURE)
                .generate());
    }
}
