package ch.admin.bj.swiyu.verifier.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MonitoringBasicAuthPropertiesTest {

    private static final String VALID_MONITORING_USERNAME = "prometheus-user";
    private static final String VALID_MONITORING_PASSWORD = "correct-prometheus-password";

    @Test
    void testConfigCheckBothFieldsSet() {
        final MonitoringBasicAuthProperties configuration = new MonitoringBasicAuthProperties();
        configuration.setUsername(VALID_MONITORING_USERNAME);
        assertDoesNotThrow(configuration::init);

        configuration.setEnabled(true);
        assertThrows(IllegalArgumentException.class, configuration::init);

        configuration.setPassword(VALID_MONITORING_PASSWORD);
        assertDoesNotThrow(configuration::init);

        configuration.setUsername("");
        assertThrows(IllegalArgumentException.class, configuration::init);
    }
}
