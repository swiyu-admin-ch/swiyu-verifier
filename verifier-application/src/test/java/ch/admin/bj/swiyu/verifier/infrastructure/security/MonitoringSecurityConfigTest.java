package ch.admin.bj.swiyu.verifier.infrastructure.security;

import ch.admin.bj.swiyu.verifier.Application;
import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.infrastructure.config.MonitoringBasicAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(classes = Application.class)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@TestPropertySource(properties = {
        "monitoring.basic-auth.enabled=true",
        "monitoring.basic-auth.username=foo",
        "monitoring.basic-auth.password=bar",
        "management.endpoint.metrics.enabled=true",
        "management.endpoint.metrics.access=read_only"
})
class MonitoringSecurityConfigTest {

    private static final String MONITORING_ENDPOINT = "/actuator/prometheus";

    @Autowired
    private MockMvc mvc;

    @Test
    void testAccessingMonitoring_withoutBasicAuth_thenFailure() {
        assertDoesNotThrow(() -> mvc.perform(get(MONITORING_ENDPOINT))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    void testAccessingMonitoring_withIncorrectBasicAuth_thenFailure() {
        String encoding = Base64.getEncoder().encodeToString(("incorrect:credentials").getBytes());
        assertDoesNotThrow(() -> mvc.perform(get(MONITORING_ENDPOINT)
                        .header("Authorization", "Basic " + encoding))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    void testAccessingMonitoring_withCorrectBasicAuth_thenSuccess() {
        String encoding = Base64.getEncoder().encodeToString(("foo:bar").getBytes());
        assertDoesNotThrow(() -> mvc.perform(get(MONITORING_ENDPOINT)
                        .header("Authorization", "Basic " + encoding))
                // during the test, prometheus endpoint is disabled, so a 404 is returned.
                // However, that still means the request got past authorization.
                .andExpect(status().isNotFound()));

    }

    @Test
    void testConfigCheckBothFieldsSet() {
        var configuration = new MonitoringBasicAuthProperties();
        configuration.setUsername("foo");
        assertDoesNotThrow(configuration::init);

        configuration.setEnabled(true);
        assertThrows(IllegalArgumentException.class, configuration::init);

        configuration.setPassword("bar");
        assertDoesNotThrow(configuration::init);

        configuration.setUsername("");
        assertThrows(IllegalArgumentException.class, configuration::init);
    }
}
