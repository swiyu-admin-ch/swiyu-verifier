package ch.admin.bj.swiyu.verifier.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("monitoring.basic-auth")
public class MonitoringBasicAuthProperties {
    boolean enabled;
    String username;
    String password;

    @PostConstruct
    public void init() {
        if (enabled) {
            if (username == null || username.isEmpty()) {
                throw new IllegalArgumentException("Property monitoring.basic-auth.username can't be empty if monitoring.basic-auth.enabled is set.");
            }
            if (password == null || password.isEmpty()) {
                throw new IllegalArgumentException("Property monitoring.basic-auth.password can't be empty if monitoring.basic-auth.enabled is set.");
            }
        }
    }
}