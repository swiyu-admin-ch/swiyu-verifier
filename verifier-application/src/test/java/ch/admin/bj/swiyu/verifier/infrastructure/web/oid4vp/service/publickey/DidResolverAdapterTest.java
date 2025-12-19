package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.service.publickey;

import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverException;
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
class DidResolverAdapterTest {
    @Autowired
    DidResolverAdapter didResolverAdapter;

    @Test
    void testResolveNullDid() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> didResolverAdapter.resolveDid(null));
    }

    @Test
    void testEmptyDid() {
        Assertions.assertThrows(DidResolverException.class, () -> didResolverAdapter.resolveDid(""));
    }
}
