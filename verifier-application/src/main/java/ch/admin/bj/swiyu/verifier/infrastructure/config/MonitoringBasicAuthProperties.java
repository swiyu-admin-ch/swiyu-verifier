package ch.admin.bj.swiyu.verifier.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@Slf4j
@ConfigurationProperties("monitoring.basic-auth")
public class MonitoringBasicAuthProperties {
    boolean enabled;
    String username;
    String password;

    @PostConstruct
    public void init() {
        if (enabled) {

            if (username == null || username.isEmpty()) {
                var msg = "Property 'monitoring.basic-auth.username' can't be empty if 'monitoring.basic-auth.enabled' is set.";
                log.error("⚠️ {}", msg);
                throw new IllegalArgumentException(msg);
            }

            if (password == null || password.isEmpty()) {
                var msg = "Property 'monitoring.basic-auth.password' can't be empty if 'monitoring.basic-auth.enabled' is set.";
                log.error("⚠️ {}", msg);
                throw new IllegalArgumentException(msg);
            }
        }
    }
}