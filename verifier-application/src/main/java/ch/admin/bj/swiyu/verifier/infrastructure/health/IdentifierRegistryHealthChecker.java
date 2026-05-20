package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Health checker that validates DID resolution for configured identifier registries.
 *
 * <p>Can be disabled via {@code management.health.identifier-registry-enabled=false}
 * (env: {@code IDENTIFIER_REGISTRY_HEALTH_ENABLED=false}).</p>
 */
@Component
@Slf4j
public class IdentifierRegistryHealthChecker extends CachedHealthChecker {

    private final List<String> didIds;
    private final DidResolverFacade didResolverFacade;
    private final HealthCheckProperties healthCheckProperties;

    public IdentifierRegistryHealthChecker(DidResolverFacade didResolverFacade,
                                           @Value("${management.endpoint.health.identifierRegistries}") List<String> didIds,
                                           HealthCheckProperties healthCheckProperties) {
        this.didResolverFacade = didResolverFacade;
        this.didIds = didIds;
        this.healthCheckProperties = healthCheckProperties;
    }

    @Override
    protected boolean isEnabled() {
        return healthCheckProperties.isIdentifierRegistryEnabled();
    }

    @Override
    protected void performCheck(Health.Builder builder) throws Exception {
        var details = didIds.stream().collect(Collectors.toMap(s -> s, didId -> {
            try {
                var didDoc = didResolverFacade.resolveDid(didId);
                didDoc.close();
                return Status.UP;
            } catch (DidResolverException | DidSidekicksException e) {
                log.debug("Failed to resolve did {} for health check: {}", didId, e.getMessage());
            }
            return Status.DOWN;
        }));
        builder.withDetails(details);

        if (details.entrySet().stream().anyMatch(s -> s.getValue().equals(Status.DOWN))) {
            builder.down();
        } else {
            builder.up();
        }
    }
}
