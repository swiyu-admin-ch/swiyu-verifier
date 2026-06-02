package ch.admin.bj.swiyu.verifier.infrastructure.security;

import ch.admin.bj.swiyu.verifier.Application;
import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.infrastructure.config.MonitoringBasicAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@TestPropertySource(properties = {
        "monitoring.basic-auth.enabled=true",
        "monitoring.basic-auth.username=prometheus-user",
        "monitoring.basic-auth.password=correct-prometheus-password",
        "management.endpoint.metrics.access=read_only",
        "management.endpoint.prometheus.access=none"
})
class MonitoringSecurityConfigTest {

    private static final String MONITORING_ENDPOINT = "/actuator/prometheus";
    private static final String HEALTH_ENDPOINT = "/actuator/health";
    private static final String VALID_MONITORING_USERNAME = "prometheus-user";
    private static final String VALID_MONITORING_PASSWORD = "correct-prometheus-password";
    private static final String INVALID_MONITORING_USERNAME = "unknown-prometheus-user";
    private static final String INVALID_MONITORING_PASSWORD = "wrong-prometheus-password";

    @Autowired
    private MockMvc mvc;

    @Test
    void testAccessingMonitoring_withoutBasicAuth_thenFailure() {
        assertDoesNotThrow(() -> mvc.perform(get(MONITORING_ENDPOINT))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    void testAccessingMonitoring_withIncorrectBasicAuth_thenFailure() {
        assertDoesNotThrow(() -> mvc.perform(get(MONITORING_ENDPOINT)
                        .header("Authorization", basicAuth(INVALID_MONITORING_USERNAME, INVALID_MONITORING_PASSWORD)))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    void testAccessingMonitoring_withCorrectBasicAuth_thenSuccess() {
        assertDoesNotThrow(() -> mvc.perform(get(MONITORING_ENDPOINT)
                        .header("Authorization", basicAuth(VALID_MONITORING_USERNAME, VALID_MONITORING_PASSWORD)))
                // The prometheus endpoint is disabled in this test, so 404 is returned.
                // That still means the request got past authorization.
                .andExpect(status().isNotFound()));
    }

    @Test
    void health_whenPrometheusBasicAuthEnabled_thenStillPublic() {
        assertDoesNotThrow(() -> mvc.perform(get(HEALTH_ENDPOINT))
                .andExpect(status().isOk()));
    }

    @Test
    void testConfigCheckBothFieldsSet() {
        var configuration = new MonitoringBasicAuthProperties();
        configuration.setUsername(VALID_MONITORING_USERNAME);
        assertDoesNotThrow(configuration::init);

        configuration.setEnabled(true);
        assertThrows(IllegalArgumentException.class, configuration::init);

        configuration.setPassword(VALID_MONITORING_PASSWORD);
        assertDoesNotThrow(configuration::init);

        configuration.setUsername("");
        assertThrows(IllegalArgumentException.class, configuration::init);
    }

    private static String basicAuth(String username, String password) {
        final String credentials = "%s:%s".formatted(username, password);
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
