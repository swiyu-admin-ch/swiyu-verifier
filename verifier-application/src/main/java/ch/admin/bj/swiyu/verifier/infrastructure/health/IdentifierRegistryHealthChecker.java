package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class IdentifierRegistryHealthChecker extends CachedHealthChecker {

    private final List<String> didIds;
    private final DidResolverFacade didResolverFacade;


    public IdentifierRegistryHealthChecker(DidResolverFacade didResolverFacade, @Value("${management.endpoint.health.identifierRegistries}") List<String> didIds) {
        this.didResolverFacade = didResolverFacade;
        this.didIds = didIds;
    }

    @Override
    protected void performCheck(Health.Builder builder) throws Exception {
        var success = new Object() {
            boolean success = true;
        };
        var details = didIds.stream().collect(Collectors.toMap(s -> s, didId -> {
            try {
                var didDoc = didResolverFacade.resolveDid(didId);
                didDoc.close();
                return Status.UP;
            } catch (DidResolverException | DidSidekicksException ignoredException) { }
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
