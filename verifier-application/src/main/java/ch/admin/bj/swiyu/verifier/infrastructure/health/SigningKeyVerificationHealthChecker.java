package ch.admin.bj.swiyu.verifier.infrastructure.health;


import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Health checker that validates the reachability / resolvability of DID (or DID methode) identifiers
 * configured for the verifier.
 */
@Component
@RequiredArgsConstructor
public class SigningKeyVerificationHealthChecker extends CachedHealthChecker {

    /** Resolver used to attempt key / DID resolution */
    private final DidResolverAdapter didResolverAdapter;

    private final ApplicationProperties applicationProperties;

    /**
     * Performs the health check logic.
     * Checks if the configured signing key verification method's DID can be resolved.
     * If resolution fails, marks health as DOWN and lists failed DIDs.
     */
    @Override
    protected void performCheck(Health.Builder builder) {

        String signingKeyVerificationMethod = applicationProperties.getSigningKeyVerificationMethod();

        List<String> failed = new ArrayList<>();

        // Check signing key verification method DID
        if (!resolveDid(signingKeyVerificationMethod)) {
            failed.add(signingKeyVerificationMethod);
        }

        if (failed.isEmpty()) {
            builder.withDetail("resolved", "all DIDs ok");
        } else {
            builder.down().withDetail("failedDids", failed);
        }
    }


    /**
     * Attempts to resolve the DID extracted from the given verification method.
     *
     * @param didMethode The verification method string containing the DID.
     * @return true if resolution is successful, false otherwise.
     */
    private boolean resolveDid(String didMethode) {
        if (didMethode == null || didMethode.isBlank()) {
            return false;
        }
        try {
            var did = removeFragmentFromVerificationMethode(didMethode);
            didResolverAdapter.resolveDid(did);
            return true;
        } catch (DidResolverException e) {
            return false;
        }
    }

    /**
     * Removes the fragment part (after '#') from a verification method to get the base DID.
     *
     * @param didMethode The verification method string.
     * @return The DID without the fragment.
     */
    private String removeFragmentFromVerificationMethode(String didMethode) {
        if (didMethode.contains("#")) {
            return didMethode.substring(0, didMethode.indexOf("#"));
        }
        return didMethode;
    }
}