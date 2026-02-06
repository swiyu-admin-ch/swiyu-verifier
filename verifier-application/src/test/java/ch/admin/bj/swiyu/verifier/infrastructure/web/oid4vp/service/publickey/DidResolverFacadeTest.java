package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.service.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;
import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.eid.did_sidekicks.DidDoc;
import ch.admin.eid.did_sidekicks.Jwk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Transactional
class DidResolverFacadeTest {

    @Autowired
    DidResolverFacade didResolverFacade;

    @Autowired
    DidResolverAdapter didResolverAdapter;

    @TestConfiguration
    static class MockConfig {
        @Bean
        public DidResolverAdapter didResolverAdapter() {
            return Mockito.mock(DidResolverAdapter.class);
        }
    }

    private static final String FRAGMENT = "key-1";

    @Test
    void testResolveNullDid() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> didResolverFacade.resolveDid(null, FRAGMENT));
    }

    @Test
    void testEmptyDid() throws DidResolverException {
        Mockito.when(didResolverAdapter.resolveDid(Mockito.eq(""), Mockito.any()))
                .thenThrow(new DidResolverException("empty did"));

        Assertions.assertThrows(DidResolverException.class, () -> didResolverFacade.resolveDid("", FRAGMENT));
    }

    @Test
    void testResolveDidIsCached() throws Exception {
        String did = "did:test:123";
        DidDoc didDoc = Mockito.mock(DidDoc.class);
        Jwk jwk = Mockito.mock(Jwk.class);

        Mockito.when(didResolverAdapter.resolveDid(Mockito.eq(did), Mockito.any()))
                .thenReturn(didDoc);
        Mockito.when(didDoc.getKey(FRAGMENT)).thenReturn(jwk);

        Jwk first = didResolverFacade.resolveDid(did, FRAGMENT);
        Jwk second = didResolverFacade.resolveDid(did, FRAGMENT);

        Assertions.assertSame(first, second, "Expected cached Jwk instance to be returned on subsequent call");
        Mockito.verify(didResolverAdapter, Mockito.times(1))
                .resolveDid(Mockito.eq(did), Mockito.any());
    }

    @Test
    void testCacheNotFilledOnException() throws Exception {
        String did = "did:test:fail";

        Mockito.when(didResolverAdapter.resolveDid(Mockito.eq(did), Mockito.any()))
                .thenThrow(new DidResolverException("fail"))
                .thenReturn(Mockito.mock(DidDoc.class));

        // First call: Exception
        Assertions.assertThrows(DidResolverException.class, () -> didResolverFacade.resolveDid(did, FRAGMENT));

        // Second
        didResolverFacade.resolveDid(did, FRAGMENT);

        Mockito.verify(didResolverAdapter, Mockito.times(2)).resolveDid(Mockito.eq(did), Mockito.any());
    }

}
