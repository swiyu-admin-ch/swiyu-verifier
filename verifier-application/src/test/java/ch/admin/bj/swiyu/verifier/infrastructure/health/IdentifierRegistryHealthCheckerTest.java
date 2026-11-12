package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.eid.did_sidekicks.DidDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidResolverTestHelper.stubResolveDid;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidResolverTestHelper.stubResolveDidFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdentifierRegistryHealthCheckerTest {
    private final String didId1 = "did:webvh:scid:example.com";
    private final String didId2 = "did:webvh:scid:test.com";

    @Mock
    DidResolverFacade didResolverFacade;
    @Mock
    DidDoc didDoc;
    @Mock
    HealthCheckProperties healthCheckProperties;

    List<String> didIds = List.of(didId1, didId2);

    private IdentifierRegistryHealthChecker identifierRegistryHealthChecker;

    @BeforeEach
    void setUp() {
        this.identifierRegistryHealthChecker = new IdentifierRegistryHealthChecker(didResolverFacade, didIds, healthCheckProperties);
    }


    @Test
    void performCheck_shouldReturnUp_whenAllDidsResolve() throws Exception {

        stubResolveDid(didResolverFacade, didId1, didDoc);
        stubResolveDid(didResolverFacade, didId2, didDoc);

        var builder = Health.unknown();
        identifierRegistryHealthChecker.performCheck(builder);
        var health = builder.build();
        assertEquals(Status.UP, health.getStatus());

        verify(didResolverFacade).resolveDid(didId1);
        verify(didResolverFacade).resolveDid(didId2);

        assertNotNull(health.getDetails().get(didId1));
        assertNotNull(health.getDetails().get(didId2));
        assertEquals(Status.UP, ((Status) health.getDetails().get(didId1)));
        assertEquals(Status.UP, ((Status) health.getDetails().get(didId2)));
    }

    @Test
    void performCheck_shouldReturnDown_whenOneDidResolveFails() throws Exception {
        stubResolveDid(didResolverFacade, didId1, didDoc);
        stubResolveDidFailure(didResolverFacade, didId2);

        var builder = Health.unknown();
        identifierRegistryHealthChecker.performCheck(builder);
        var health = builder.build();
        assertEquals(Status.DOWN, health.getStatus());

        assertNotNull(health.getDetails().get(didId1));
        assertNotNull(health.getDetails().get(didId2));
        assertEquals(Status.UP, ((Status) health.getDetails().get(didId1)));
        assertEquals(Status.DOWN, ((Status) health.getDetails().get(didId2)));
    }

    @Test
    void performCheck_shouldReturnDown_whenAllDidResolvesFails() throws Exception {
        stubResolveDidFailure(didResolverFacade, didId1);
        stubResolveDidFailure(didResolverFacade, didId2);

        var builder = Health.unknown();
        identifierRegistryHealthChecker.performCheck(builder);
        var health = builder.build();
        assertEquals(Status.DOWN, health.getStatus());

        assertNotNull(health.getDetails().get(didId1));
        assertNotNull(health.getDetails().get(didId2));
        assertEquals(Status.DOWN, ((Status) health.getDetails().get(didId1)));
        assertEquals(Status.DOWN, ((Status) health.getDetails().get(didId2)));
    }
}
