package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.FormatAlgorithm;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.jayway.jsonpath.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static ch.admin.bit.eid.oid4vp.mock.CredentialSubmissionMock.*;
import static ch.admin.bit.eid.oid4vp.mock.ManagementEntityMock.getManagementEntityMock;
import static ch.admin.bit.eid.oid4vp.mock.PresentationDefinitionMocks.createPresentationDefinitionMock;
import static ch.admin.bit.eid.oid4vp.mock.PresentationDefinitionMocks.createPresentationDefinitionMockWithDescriptorFormat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
class VerificationServiceTest {

    private final UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");

    @Autowired
    private VerificationService verificationService;

    @MockBean
    private ApplicationConfiguration applicationConfiguration;

    @MockBean
    private VerificationManagementRepository verificationManagementRepository;


    @Test
    void contextLoads() {
        assertThat(verificationService).isNotNull();
    }

    @Test
    void testProcessPresentation_thenIllegalArgumentException() {

        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate"));
        ManagementEntity managementEntity = getManagementEntityMock(requestId, presentationDefinition);
        PresentationSubmission presentationSubmission = getPresentationDefinitionMock(1, false);
        Object emptyDocument = new Object();

        assertThrows(IllegalArgumentException.class, () -> verificationService.getPathToSupportedCredential(managementEntity, null, presentationSubmission));
        assertThrows(IllegalArgumentException.class, () -> verificationService.getPathToSupportedCredential(managementEntity, emptyDocument, null));
    }

    @Test
    void testProcessPresentation_thenSuccess() {
        String vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\"}}]}]";

        PresentationSubmission presentationSubmission = getPresentationDefinitionMock(1, true);
        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate"));
        ManagementEntity managementEntity = getManagementEntityMock(requestId, presentationDefinition);
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(vpToken);

        assertEquals(CredentialPathList, verificationService.getPathToSupportedCredential(managementEntity, document, presentationSubmission));
    }

    @Test
    void testProcessPresentationIncompleteData_thenThrowException() {
        String vpToken = "{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\"}}]}";

        testProcessWithToken(vpToken);
    }

    @Test
    void testProcessPresentationWrongToken_thenThrowException() {
        String vpToken = "[\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\"}}]}";

        testProcessWithToken(vpToken);
    }

    private void testProcessWithToken(String vpToken) {
        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        ManagementEntity managementEntity = getManagementEntityMock(requestId, presentationDefinition);
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(vpToken);

        assertThrows(VerificationException.class, () -> verificationService.checkPresentationDefinitionCriteria(document, CredentialPath, managementEntity));
    }

    @Test
    void testListCredential_thenSuccess() {
        String vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        ManagementEntity managementEntity = getManagementEntityMock(UUID.randomUUID(), presentationDefinition);
        PresentationSubmission presentationSubmission = getPresentationDefinitionMock(2, true);

        assertEquals(CredentialPathList, verificationService.getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));
    }

    @Test
    void testListCredentialNotMatchingSubmission_thenError() {
        String vpToken = "[{\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\", \"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        ManagementEntity managementEntity = getManagementEntityMock(UUID.randomUUID(), presentationDefinition);
        PresentationSubmission presentationSubmission = getPresentationDefinitionMock(1, false);
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(vpToken);

        assertThrows(VerificationException.class, () -> verificationService.getPathToSupportedCredential(managementEntity, document, presentationSubmission));
    }

    @Test
    void test1ListCredentialNotMatchingSubmission_thenError() {
        String vpToken = "[{\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\", \"zip\": \"1000\"}}]}]";

        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        ManagementEntity managementEntity = getManagementEntityMock(UUID.randomUUID(), presentationDefinition);
        PresentationSubmission presentationSubmission = getPresentationDefinitionMock(2, false);
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(vpToken);

        assertThrows(VerificationException.class, () -> verificationService.getPathToSupportedCredential(managementEntity, document, presentationSubmission));
    }

    @Test
    void testWhenNoSupportedCredFormats_thenThrowException() {
        String vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        ManagementEntity managementEntity = getManagementEntityMock(UUID.randomUUID(), presentationDefinition);
        PresentationSubmission presentationSubmission = getPresentationDefinitionMockWithFormat(2, true, "wrong_format");
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(vpToken);

        VerificationException exception = assertThrows(VerificationException.class, () -> verificationService.getPathToSupportedCredential(managementEntity, document, presentationSubmission));

        assertEquals(VerificationErrorEnum.INVALID_REQUEST, exception.getError().getError());
        assertEquals(ResponseErrorCodeEnum.CREDENTIAL_INVALID, exception.getError().getErrorCode());
        assertEquals("No matching paths with correct formats found", exception.getError().getErrorDescription());
    }

    @Test
    void testWhenNoSupportedCredFormatsInDescriptor_thenThrowException() {
        String vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        HashMap<String, FormatAlgorithm> formats = new HashMap<>();
        formats.put("ldp_vp", FormatAlgorithm.builder()
                .proofType(List.of("BBS2023"))
                .build());

        PresentationDefinition presentationDefinition = createPresentationDefinitionMockWithDescriptorFormat(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"), formats);
        ManagementEntity managementEntity = getManagementEntityMock(UUID.randomUUID(), presentationDefinition);
        PresentationSubmission presentationSubmission = getPresentationDefinitionMockWithFormat(2, true, "wrong_format");
        Object document = Configuration.defaultConfiguration().jsonProvider().parse(vpToken);

        VerificationException exception = assertThrows(VerificationException.class, () -> verificationService.getPathToSupportedCredential(managementEntity, document, presentationSubmission));

        assertEquals(VerificationErrorEnum.INVALID_REQUEST, exception.getError().getError());
        assertEquals(ResponseErrorCodeEnum.CREDENTIAL_INVALID, exception.getError().getErrorCode());
        assertEquals("No matching paths with correct formats found", exception.getError().getErrorDescription());
    }

    @Test
    void testWhenCredFormatsInDescriptor_thenSuccess() {
        String vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        HashMap<String, FormatAlgorithm> formats = new HashMap<>();
        formats.put("ldp_vp", FormatAlgorithm.builder()
                .proofType(List.of("BBS2023"))
                .build());

        PresentationDefinition presentationDefinition = createPresentationDefinitionMockWithDescriptorFormat(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"), formats);
        ManagementEntity managementEntity = getManagementEntityMock(UUID.randomUUID(), presentationDefinition);
        PresentationSubmission presentationSubmission = getPresentationDefinitionMockWithFormat(2, true, "ldp_vp");

        assertEquals(CredentialPathList, verificationService.getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));
    }

    @Test
    void testWhenNoFormats_thenSuccess() {
        String vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\",\"zip\": \"1000\"}}]}, {\"verifiableCredential\": [{\"credentialSubject\": {\"zip\": \"1000\"}}]}]";

        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"), null, null);
        ManagementEntity managementEntity = getManagementEntityMock(UUID.randomUUID(), presentationDefinition);
        PresentationSubmission presentationSubmission = getPresentationDefinitionMockWithFormat(2, true, "ldp_vp");

        VerificationException exception = assertThrows(VerificationException.class, () -> verificationService.getPathToSupportedCredential(managementEntity, vpToken, presentationSubmission));

        assertEquals(VerificationErrorEnum.INVALID_REQUEST, exception.getError().getError());
        assertEquals(ResponseErrorCodeEnum.CREDENTIAL_INVALID, exception.getError().getErrorCode());
        assertEquals("No matching paths with correct formats found", exception.getError().getErrorDescription());
    }
}
