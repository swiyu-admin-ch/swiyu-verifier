package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.service.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;

import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
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
}
