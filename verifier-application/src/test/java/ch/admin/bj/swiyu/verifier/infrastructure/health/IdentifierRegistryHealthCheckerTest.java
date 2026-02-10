package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class IdentifierRegistryHealthCheckerTest {
    private final String didId1 = "did:webvh:scid:example.com";
    private final String didId2 = "did:webvh:scid:test.com";


    @Mock
    DidResolverFacade didResolverFacade;
    List<String> didIds = List.of(didId1, didId2);

    private IdentifierRegistryHealthChecker identifierRegistryHealthChecker;

    @BeforeEach
    void setUp() {
        this.identifierRegistryHealthChecker = new IdentifierRegistryHealthChecker(didResolverFacade, didIds);
    }


    @Test
    void performCheck_shouldReturnUp_whenAllDidsResolve() throws Exception {
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
        var builder = Health.unknown();
        when(didResolverFacade.resolveDid(didId1)).thenReturn(null);
        when(didResolverFacade.resolveDid(didId2)).thenThrow(new DidResolverException("Test"));

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
        var builder = Health.unknown();
        when(didResolverFacade.resolveDid(any())).thenThrow(new DidResolverException("test exception"));

        identifierRegistryHealthChecker.performCheck(builder);
        var health = builder.build();
        assertEquals(Status.DOWN, health.getStatus());

        assertNotNull(health.getDetails().get(didId1));
        assertNotNull(health.getDetails().get(didId2));
        assertEquals(Status.DOWN, ((Status) health.getDetails().get(didId1)));
        assertEquals(Status.DOWN, ((Status) health.getDetails().get(didId2)));
    }

}
