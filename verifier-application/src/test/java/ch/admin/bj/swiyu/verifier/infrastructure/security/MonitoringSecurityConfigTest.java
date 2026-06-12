package ch.admin.bj.swiyu.verifier.infrastructure.security;

import ch.admin.bj.swiyu.verifier.infrastructure.config.MonitoringBasicAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(useDefaultFilters = false)
@Import({WebSecurityConfig.class, MonitoringSecurityConfig.class})
@EnableConfigurationProperties(MonitoringBasicAuthProperties.class)
@TestPropertySource(properties = {
        "monitoring.basic-auth.enabled=true",
        "monitoring.basic-auth.username=prometheus-user",
        "monitoring.basic-auth.password=correct-prometheus-password"
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
    void testAccessingMonitoring_withoutBasicAuth_thenFailure() throws Exception {
        mvc.perform(get(MONITORING_ENDPOINT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccessingMonitoring_withIncorrectBasicAuth_thenFailure() throws Exception {
        mvc.perform(get(MONITORING_ENDPOINT)
                        .header("Authorization", basicAuth(INVALID_MONITORING_USERNAME, INVALID_MONITORING_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAccessingMonitoring_withCorrectBasicAuth_thenSuccess() throws Exception {
        mvc.perform(get(MONITORING_ENDPOINT)
                        .header("Authorization", basicAuth(VALID_MONITORING_USERNAME, VALID_MONITORING_PASSWORD)))
                // The prometheus endpoint is disabled in this test, so 404 is returned.
                // That still means the request got past authorization.
                .andExpect(status().isNotFound());
    }

    @Test
    void health_whenPrometheusBasicAuthEnabled_thenStillPublic() throws Exception {
        mvc.perform(get(HEALTH_ENDPOINT))
                .andExpect(status().isNotFound());
    }

    private static String basicAuth(String username, String password) {
        final String credentials = "%s:%s".formatted(username, password);
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
