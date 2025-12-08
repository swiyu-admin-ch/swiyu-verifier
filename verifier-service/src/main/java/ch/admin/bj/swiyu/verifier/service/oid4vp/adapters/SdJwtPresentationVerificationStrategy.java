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
 * Strategy adapter for the format "vc+sd-jwt".
 * Uses the existing PresentationVerifier<String> port.
 */
@Component
@RequiredArgsConstructor
public class SdJwtPresentationVerificationStrategy implements PresentationVerificationStrategy {

    private final PresentationVerifier<String> stringPresentationVerifier;

    @Override
    public String verify(String vpToken, Management managementEntity, PresentationSubmissionDto presentationSubmission) {
        var credentialToBeProcessed = VerifiableCredentialExtractor.extractVerifiableCredential(vpToken, managementEntity, presentationSubmission);
        return stringPresentationVerifier.verify(credentialToBeProcessed, managementEntity);
    }

    @Override
    public String getSupportedFormat() {
        return SdjwtCredentialVerifier.CREDENTIAL_FORMAT;
    }
}
