/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp.service;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationError;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;

import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.VerifiableCredentialExtractor.getPathToSupportedCredential;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.CredentialSubmissionFixtures.*;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.ManagementFixtures.managementEntity;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.PresentationDefinitionFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerifiableCredentialExtractorTest {

    private final UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");

    @Test
    void testProcessPresentation_thenIllegalArgumentException() {

        var presentationDefinition = sdwjtPresentationDefinition(requestId, List.of("$.first_name", "$.last_name", "$.birthdate"));
        var managementEntity = managementEntity(requestId, presentationDefinition);
        var presentationSubmission = presentationSubmission(1, false);

        assertThrows(IllegalArgumentException.class, () -> getPathToSupportedCredential(managementEntity, null, presentationSubmission));
        assertThrows(IllegalArgumentException.class, () -> getPathToSupportedCredential(managementEntity, "", null));
    }

    @Test
    void testProcessPresentation_thenSuccess() {
        var vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\"}}]}]";

        var presentationSubmission = presentationSubmission(1, true);
        var presentationDefinition = sdwjtPresentationDefinition(requestId, List.of("$.first_name", "$.last_name", "$.birthdate"));
        var managementEntity = managementEntity(requestId, presentationDefinition);

        assertEquals(CredentialPathList, getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));
    }

    @Test
    void testListCredential_thenSuccess() {
        String vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        var presentationDefinition = sdwjtPresentationDefinition(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        var managementEntity = managementEntity(UUID.randomUUID(), presentationDefinition);
        var presentationSubmission = presentationSubmission(2, true);

        assertEquals(CredentialPathList, getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));
    }

    @Test
    void testWhenNoSupportedCredFormats_thenThrowException() {
        var vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        var presentationDefinition = sdwjtPresentationDefinition(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        var managementEntity = managementEntity(UUID.randomUUID(), presentationDefinition);
        var presentationSubmission = presentationSubmissionWithFormat(2, true, "wrong_format");

        var exception = assertThrows(VerificationException.class, () -> getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));

        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED, exception.getErrorResponseCode());
        assertEquals("No matching paths with correct formats found", exception.getErrorDescription());
    }

    @Test
    void testWhenNoSupportedCredFormatsInDescriptor_thenThrowException() {
        var vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        HashMap<String, PresentationDefinition.FormatAlgorithm> formats = new HashMap<>();
        formats.put("ldp_vp", PresentationDefinition.FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256"))
                .build());

        var presentationDefinition = presentationDefinitionWithDescriptorFormat(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"), formats);
        var managementEntity = managementEntity(UUID.randomUUID(), presentationDefinition);
        var presentationSubmission = presentationSubmissionWithFormat(2, true, "wrong_format");

        var exception = assertThrows(VerificationException.class, () -> getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));

        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals(VerificationErrorResponseCode.PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED, exception.getErrorResponseCode());
        assertEquals("No matching paths with correct formats found", exception.getErrorDescription());
    }

    @Test
    void testWhenCredFormatsInDescriptor_thenSuccess() {
        var vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        HashMap<String, PresentationDefinition.FormatAlgorithm> formats = new HashMap<>();
        formats.put("vc+sd-jwt", PresentationDefinition.FormatAlgorithm.builder()
                .keyBindingAlg(List.of("ES256"))
                .alg(List.of("ES256"))
                .build());

        var presentationDefinition = presentationDefinitionWithDescriptorFormat(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"), formats);
        var managementEntity = managementEntity(UUID.randomUUID(), presentationDefinition);
        var presentationSubmission = presentationSubmissionWithFormat(2, true, "vc+sd-jwt");

        assertEquals(CredentialPathList, getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));
    }

    @Test
    void testWhenNoFormats_thenError() {
        var vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        var presentationDefinition = presentationDefinition(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"), null, null);
        var managementEntity = managementEntity(UUID.randomUUID(), presentationDefinition);
        var presentationSubmission = presentationSubmissionWithFormat(2, true, "vc+sd-jwt");
        var exception = assertThrows(VerificationException.class, () -> getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));

        assertEquals(VerificationError.INVALID_CREDENTIAL, exception.getErrorType());
        assertEquals("No matching paths with correct formats found", exception.getErrorDescription());
    }

}
