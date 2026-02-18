package ch.admin.bj.swiyu.verifier.infrastructure.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class VerifierHealthIndicatorTest {

    @Mock
    private SigningKeyVerificationHealthChecker signingKeyHealthChecker;

    private VerifierHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new VerifierHealthIndicator(this.signingKeyHealthChecker);
    }

    @Test
    void performCheck_shouldReturnUp_whenSigningKeyHealthCheckerIsUp() {
        when(signingKeyHealthChecker.getHealthResult()).thenReturn(Health.up().build());

        var health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("signingKeyVerification")).isNotNull();
    }

    @Test
    void performCheck_shouldReturnDown_whenSigningKeyHealthCheckerIsDown() {
        when(signingKeyHealthChecker.getHealthResult()).thenReturn(Health.down().build());

        var health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void performCheck_shouldIncludeHealthDetails() {
        Map<String, Object> details = Map.of("foo", "bar");
        when(signingKeyHealthChecker.getHealthResult()).thenReturn(Health.up().withDetails(details).build());

        var health = healthIndicator.health();
        assertThat(health.getDetails()).containsKey("signingKeyVerification");
        assertThat(health.getDetails().get("signingKeyVerification")).isInstanceOf(Map.class);
        var retrievedDetails = health.getDetails().get("signingKeyVerification");
        assertThat(retrievedDetails).isEqualTo(details);
    }
}
