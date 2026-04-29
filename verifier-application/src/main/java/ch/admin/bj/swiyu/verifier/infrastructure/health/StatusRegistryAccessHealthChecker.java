package ch.admin.bj.swiyu.verifier.infrastructure.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Health checker that verifies accessibility of configured status registry URLs.
 *
 * <p>Can be disabled via {@code application.health.status-registry-enabled=false}
 * (env: {@code STATUS_REGISTRY_HEALTH_ENABLED=false}).</p>
 */
@Component
public class StatusRegistryAccessHealthChecker extends CachedHealthChecker {

    private final WebClient webClient;
    private final List<URI> statusListsToCheck;
    private final HealthCheckProperties healthCheckProperties;

    public StatusRegistryAccessHealthChecker(WebClient webClient,
                                             @Value("${management.endpoint.health.serviceregistries}") List<URI> urls,
                                             HealthCheckProperties healthCheckProperties) {
        this.webClient = webClient;
        this.statusListsToCheck = urls;
        this.healthCheckProperties = healthCheckProperties;
    }

    @Override
    protected boolean isEnabled() {
        return healthCheckProperties.isStatusRegistryEnabled();
    }

    @Override
    protected void performCheck(Health.Builder builder) throws Exception {
        var success = new Object() {
            boolean success = true;
        };

        var details = statusListsToCheck.stream().collect(Collectors.toMap(URI::toString, uri -> {
            var request = this.webClient.get().uri(uri);
            try {
                var response = request.retrieve().toBodilessEntity().block();
                if (response.getStatusCode().is2xxSuccessful()) {
                    return Status.UP;
                }
            } catch (WebClientException ignoredException) { }
            success.success = false;
            return Status.DOWN;
        }));
        builder.withDetails(details);

        if (success.success) {
            builder.up();
        } else {
            builder.down();
        }
    }
}
