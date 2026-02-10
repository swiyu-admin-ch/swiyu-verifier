/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

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
    @Mock
    private CallbackHealthChecker callbackHealthChecker;
    @Mock
    private StatusRegistryAccessHealthChecker statusRegistryAccessHealthChecker;
    @Mock
    private IdentifierRegistryHealthChecker identifierRegistryHealthChecker;

    private VerifierHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new VerifierHealthIndicator(this.signingKeyHealthChecker,
                this.callbackHealthChecker,
                this.statusRegistryAccessHealthChecker,
                this.identifierRegistryHealthChecker);
        System.out.println(healthIndicator);
    }

    @Test
    void performCheck_shouldReturnUp_whenSigningKeyHealthCheckerIsUp() {
        when(signingKeyHealthChecker.getHealthResult()).thenReturn(Health.up().build());
        when(callbackHealthChecker.getHealthResult()).thenReturn(Health.up().build());
        when(statusRegistryAccessHealthChecker.getHealthResult()).thenReturn(Health.up().build());
        when(identifierRegistryHealthChecker.getHealthResult()).thenReturn(Health.up().build());

        var health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("signingKeyVerification")).isNotNull();
        assertThat(health.getDetails().get("staleCallbacks")).isNotNull();
        assertThat(health.getDetails().get("statusRegistry")).isNotNull();
        assertThat(health.getDetails().get("identifierRegistry")).isNotNull();
    }

    @Test
    void performCheck_shouldReturnDown_whenOnHealthCheckerIsDown() {
        when(signingKeyHealthChecker.getHealthResult()).thenReturn(Health.down().build());
        when(callbackHealthChecker.getHealthResult()).thenReturn(Health.up().build());
        when(statusRegistryAccessHealthChecker.getHealthResult()).thenReturn(Health.up().build());
        when(identifierRegistryHealthChecker.getHealthResult()).thenReturn(Health.up().build());

        var health = healthIndicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void performCheck_shouldIncludeHealthDetails() {
        Map<String, Object> details = Map.of("foo", "bar");
        when(signingKeyHealthChecker.getHealthResult()).thenReturn(Health.up().withDetails(details).build());
        when(callbackHealthChecker.getHealthResult()).thenReturn(Health.up().build());
        when(statusRegistryAccessHealthChecker.getHealthResult()).thenReturn(Health.up().build());
        when(identifierRegistryHealthChecker.getHealthResult()).thenReturn(Health.up().build());

        var health = healthIndicator.health();
        assertThat(health.getDetails()).containsKey("signingKeyVerification");
        assertThat(health.getDetails().get("signingKeyVerification")).isInstanceOf(Map.class);
        var retrievedDetails = health.getDetails().get("signingKeyVerification");
        assertThat(retrievedDetails).isEqualTo(details);
    }
}
