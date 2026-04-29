package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEvent;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackHealthCheckerTest {

    @Mock
    CallbackEventRepository repository;

    @Mock
    HealthCheckProperties healthCheckProperties;


    @Test
    void performCheck_timeUntilStaleIsBiggerThanDispatchInterval() throws Exception {
        var dispatchInterval = Duration.ofHours(2);
        var callbackHealthChecker = new CallbackHealthChecker(this.repository, dispatchInterval, healthCheckProperties);

        var health = Health.up();
        callbackHealthChecker.performCheck(health);

        final ArgumentCaptor<Instant> timeUntilstaleCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findAllByTimestampBefore(timeUntilstaleCaptor.capture());
        assertTrue(Instant.now().minus(dispatchInterval).isAfter(timeUntilstaleCaptor.getValue()));
    }

    @Test
    void performCheck_shouldReturnUp_whenNoStaleCallbacksl() throws Exception {
        var callbackHealthChecker = new CallbackHealthChecker(this.repository, Duration.ofSeconds(2), healthCheckProperties);

        when(repository.findAllByTimestampBefore(any())).thenReturn(List.of());

        var builder = Health.up();
        callbackHealthChecker.performCheck(builder);
        var health = builder.build();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void performCheck_shouldReturnDown_whenStaleCallbacks() throws Exception {
        var callbackHealthChecker = new CallbackHealthChecker(this.repository, Duration.ofSeconds(2), healthCheckProperties);

        var callbacks = List.of(
                CallbackEvent.builder().id(UUID.randomUUID()).build(),
                CallbackEvent.builder().id(UUID.randomUUID()).build(),
                CallbackEvent.builder().id(UUID.randomUUID()).build()
        );
        when(repository.findAllByTimestampBefore(any())).thenReturn(callbacks);

        var builder = Health.up();
        callbackHealthChecker.performCheck(builder);
        var health = builder.build();

        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails().get("amountOfStaleCallbacks"));
        assertEquals(callbacks.size(), health.getDetails().get("amountOfStaleCallbacks"));
    }
}
