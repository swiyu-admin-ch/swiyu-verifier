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

@Component
public class StatusRegistryAccessHealthChecker extends CachedHealthChecker {

    private final WebClient webClient;
    private final List<URI> statusListsToCheck;

    public StatusRegistryAccessHealthChecker(WebClient webClient, @Value("${management.endpoint.health.serviceregistries}") List<URI> urls) {
        this.webClient = webClient;
        this.statusListsToCheck = urls;
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
            } catch (WebClientException ignoredException) {
                System.out.println(ignoredException);
            }
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
