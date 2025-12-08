package ch.admin.bj.swiyu.verifier.service.oid4vp.ports;

import ch.admin.bj.swiyu.verifier.domain.management.Management;

/**
 * Port: verifies a presented VP token and returns a verified result.
 * Implementations can return different types (e.g., SdJwt or String).
 */
public interface PresentationVerifier<R> {
    R verify(String vpToken, Management management);
}
