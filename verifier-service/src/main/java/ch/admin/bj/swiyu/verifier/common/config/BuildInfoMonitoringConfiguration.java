package ch.admin.bj.swiyu.verifier.common.config;


import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;

@Configuration
@Slf4j
// Only create this configuration if BuildProperties is available.
// This avoids startup failures when build info is not configured because of missing maven build
@ConditionalOnBean(BuildProperties.class)
public class BuildInfoMonitoringConfiguration {

    /**
     * Registers a prometheus metric which shows which code is currently running.
     *
     * @param buildProperties the build properties containing information about the build
     * @param meterRegistry   the meter registry to register the custom meter
     */
    BuildInfoMonitoringConfiguration(BuildProperties buildProperties, MeterRegistry meterRegistry, Environment environment) {
        // Create a custom meter named "runtime_active_count" to export information regarding the currently running build
        var metric = Meter.builder("runtime",
                        Meter.Type.OTHER,
                        // Provide a single measurement with a constant value of 1 to allow for aggregation over
                        // multiple instances of the app to gain insight how many instances run this version of the code.
                        () -> Collections.singletonList(new Measurement(() -> 1, Statistic.ACTIVE_TASKS)).iterator())
                .description("Describes the system build info") // Set the description of the meter
                // Add tags to the meter with build properties
                .tag("build_artifact_id", buildProperties.getArtifact())
                .tag("build_group_id", buildProperties.getGroup())
                .tag("build_name", buildProperties.getName())
                .tag("build_version", buildProperties.getVersion())
                .tag("build_time", buildProperties.getTime().toString())
                .tag("build_time", buildProperties.getTime().toString());

        try {
            metric.tag("current_config_hash", hashAllProperties(environment));
        } catch (Exception e) {
            log.debug("Could not add 'current_config' hash to runtime_info export", e);
            metric.tag("current_config_hash", "unknown");
        }
        metric.register(meterRegistry); // Register the meter with the meter registry
    }

    /**
     * Hashes all variables of a given environment.
     *
     * @param environment The environment to hash
     * @return A string which represents a hash of all environment variables
     */
    private String hashAllProperties(Environment environment) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();

        if (environment instanceof StandardEnvironment standardEnvironment) {
            for (PropertySource<?> propertySource : standardEnvironment.getPropertySources()) {
                if (propertySource.getSource() instanceof java.util.Map) {
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) propertySource.getSource();
                    for (java.util.Map.Entry<String, Object> entry : map.entrySet()) {
                        sb.append(entry.getKey()).append(entry.getValue());
                    }
                }
            }
        }

        byte[] hashBytes = digest.digest(sb.toString().getBytes());
        return Base64.getEncoder().encodeToString(hashBytes);
    }
}
