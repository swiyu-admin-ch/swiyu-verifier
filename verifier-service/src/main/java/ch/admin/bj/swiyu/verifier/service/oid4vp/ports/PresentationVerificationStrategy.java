package ch.admin.bj.swiyu.verifier.service.oid4vp.ports;

import ch.admin.bj.swiyu.verifier.dto.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.domain.management.Management;

/**
 * Strategy port for verifying presentations of a specific format.
 * Implementations encapsulate format-specific logic and return the
 * credential subject data as a String.
 */
public interface PresentationVerificationStrategy {

    /**
     * Verifies the presentation for the supported format.
     *
     * @param vpToken               the VP token as received
     * @param managementEntity      the management entity of the verification process
     * @param presentationSubmission the parsed and validated presentation submission
     * @return the credential subject data serialized as String
     */
    String verify(String vpToken, Management managementEntity, PresentationSubmissionDto presentationSubmission);

    /**
     * The presentation format this strategy supports (e.g. "vc+sd-jwt").
     *
     * @return the supported format identifier
     */
    String getSupportedFormat();
}
