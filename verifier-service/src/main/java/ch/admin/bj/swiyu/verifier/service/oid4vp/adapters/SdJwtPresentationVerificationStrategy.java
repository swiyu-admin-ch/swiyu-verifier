/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */
package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.api.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VerifiableCredentialExtractor;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerificationStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PresentationVerificationStrategy implementation for the credential format {@code "vc+sd-jwt"}.
 * <p>
 * This strategy acts as an adapter between the generic OID4VP verification flow and the
 * {@link PresentationVerifier} port that operates on a single SD-JWT based verifiable credential
 * represented as a {@link String}.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Extract the concrete verifiable credential from the VP token and presentation submission
 *       using {@link VerifiableCredentialExtractor}.</li>
 *   <li>Delegate the actual verification of that credential to the injected
 *       {@link PresentationVerifier} implementation.</li>
 *   <li>Expose the supported credential format so the strategy can be selected by the OID4VP engine.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SdJwtPresentationVerificationStrategy implements PresentationVerificationStrategy {

    /**
     * Verifier for SD-JWT based verifiable credentials represented as a {@link String}.
     */
    private final PresentationVerifier<String> stringPresentationVerifier;

    /**
     * Verifies a "vc+sd-jwt" verifiable presentation.
     *
     * @param vpToken                the raw VP token containing the SD-JWT based verifiable credential
     * @param managementEntity       the {@link Management} configuration used to drive verification rules
     * @param presentationSubmission the associated presentation submission describing how the VP
     *                               satisfies the presentation definition
     * @return a JSON string representing the verified and merged claims of the credential
     */
    @Override
    public String verify(String vpToken, Management managementEntity, PresentationSubmissionDto presentationSubmission) {
        var credentialToBeProcessed = VerifiableCredentialExtractor.extractVerifiableCredential(vpToken, managementEntity, presentationSubmission);
        return stringPresentationVerifier.verify(credentialToBeProcessed, managementEntity);
    }

    /**
     * Returns the OID4VP credential format supported by this strategy.
     *
     * @return the constant format identifier for SD-JWT based credentials
     */
    @Override
    public String getSupportedFormat() {
        return SdjwtCredentialVerifier.CREDENTIAL_FORMAT;
    }
}
