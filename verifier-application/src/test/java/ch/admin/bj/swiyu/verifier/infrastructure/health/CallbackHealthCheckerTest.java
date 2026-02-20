package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEvent;
import ch.admin.bj.swiyu.verifier.domain.callback.CallbackEventRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CallbackHealthCheckerTest {

    @Mock
    CallbackEventRepository repository;

    @Test
    void performCheck_timeUntilStaleIsBiggerThanDispatchInterval() throws Exception {
        var dispatchInterval = Duration.ofHours(2);
        var callbackHealthChecker = new CallbackHealthChecker(this.repository, dispatchInterval);

        var health = Health.up();
        callbackHealthChecker.performCheck(health);

        final ArgumentCaptor<Instant> timeUntilstaleCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findAllByTimestampBefore(timeUntilstaleCaptor.capture());
        Assertions.assertTrue(Instant.now().minus(dispatchInterval).isAfter(timeUntilstaleCaptor.getValue()));
    }

    @Test
    void performCheck_shouldReturnUp_whenNoStaleCallbacksl() throws Exception {
        var callbackHealthChecker = new CallbackHealthChecker(this.repository, Duration.ofSeconds(2));

        when(repository.findAllByTimestampBefore(any())).thenReturn(List.of());

        var builder = Health.up();
        callbackHealthChecker.performCheck(builder);
        var healh = builder.build();

        assertEquals(Status.UP, healh.getStatus());
    }

    @Test
    void performCheck_shouldReturnDown_whenStaleCallbacks() throws Exception {
        var callbackHealthChecker = new CallbackHealthChecker(this.repository, Duration.ofSeconds(2));

        var callbacks = List.of(
                CallbackEvent.builder().id(UUID.randomUUID()).build(),
                CallbackEvent.builder().id(UUID.randomUUID()).build(),
                CallbackEvent.builder().id(UUID.randomUUID()).build()
        );
        when(repository.findAllByTimestampBefore(any())).thenReturn(callbacks);

        var builder = Health.up();
        callbackHealthChecker.performCheck(builder);
        var healh = builder.build();

        assertEquals(Status.DOWN, healh.getStatus());
        assertNotNull(healh.getDetails().get("amountOfStaleCallbacks"));
        assertEquals(callbacks.size(), healh.getDetails().get("amountOfStaleCallbacks"));
    }
}
