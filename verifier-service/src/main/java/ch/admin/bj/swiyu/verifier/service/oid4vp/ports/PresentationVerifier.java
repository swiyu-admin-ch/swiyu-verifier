package ch.admin.bj.swiyu.verifier.service.oid4vp.ports;

import ch.admin.bj.swiyu.verifier.domain.SdJwtVerificationResult;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;

/**
 * Port: verifies a presented VP token and returns a verified result.
 */
@FunctionalInterface
public interface PresentationVerifier {
    SdJwtVerificationResult verify(String vpToken, Management management, DcqlCredential dcqlCredential);
}
