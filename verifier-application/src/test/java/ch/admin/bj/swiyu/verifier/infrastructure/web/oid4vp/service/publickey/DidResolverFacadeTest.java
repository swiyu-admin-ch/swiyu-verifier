package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.service.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void testResolveNullDid() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> didResolverFacade.resolveDid(null));
    }

    @Test
    void testEmptyDid() {
        Assertions.assertThrows(DidResolverException.class, () -> didResolverFacade.resolveDid(""));
    }
}
