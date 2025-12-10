package ch.admin.bj.swiyu.verifier.service.oid4vp.ports;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;

/**
 * Port: verifies a presented VP token and returns a verified result.
 * Implementations can return different types (e.g., SdJwt or String).
 */
public interface SdjwtPresentationVerifier {
    SdJwt verify(String vpToken, Management management, DcqlCredential dcqlCredential);
}
