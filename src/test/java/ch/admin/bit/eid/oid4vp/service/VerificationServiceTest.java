package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.Descriptor;
import ch.admin.bit.eid.oid4vp.model.PresentationSubmissionDto;
import ch.admin.bit.eid.oid4vp.model.dto.Constraint;
import ch.admin.bit.eid.oid4vp.model.dto.Field;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class VerificationServiceTest {

    @Autowired
    private VerificationService verificationService;

    @MockBean
    private ApplicationConfiguration applicationConfiguration;

    @Test
    void contextLoads() throws Exception {
        assertThat(verificationService).isNotNull();
    }

    @Test
    void testProcessPresentation_thenIllegalArgumentException() {

        ManagementEntity managementEntity = getManagementEntity(List.of("$.first_name", "$.last_name", "$.birthdate"));
        PresentationSubmissionDto presentationSubmissionDto = getPresentationDefinition(1, false);

        assertThrows(IllegalArgumentException.class, () -> verificationService.validateSubmissionComplete(managementEntity, "", presentationSubmissionDto));
        assertThrows(IllegalArgumentException.class, () -> verificationService.validateSubmissionComplete(managementEntity, "12", null));
    }

    @Test
    void testProcessPresentation_thenSuccess() {
        String vpToken = "[{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\"}}]}]";

        ManagementEntity managementEntity = getManagementEntity(List.of("$.first_name", "$.last_name", "$.birthdate"));
        PresentationSubmissionDto presentationSubmissionDto = getPresentationDefinition(1, true);

        assertTrue(verificationService.validateSubmissionComplete(managementEntity, vpToken, presentationSubmissionDto));
    }

    @Test
    void testProcessPresentationIncompleteData_thenThrowException() {
        String vpToken = "{\"type\": [\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\"}}]}";

        ManagementEntity managementEntity = getManagementEntity(List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        PresentationSubmissionDto presentationSubmissionDto = getPresentationDefinition(1, false);

        assertThrows(VerificationException.class, () -> verificationService.validateSubmissionComplete(managementEntity, vpToken, presentationSubmissionDto));
    }

    @Test
    void testProcessPresentationWrongToken_thenThrowException() {
        String vpToken = "[\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\"}}]}";

        ManagementEntity managementEntity = getManagementEntity(List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        PresentationSubmissionDto presentationSubmissionDto = getPresentationDefinition(1, false);

        assertThrows(VerificationException.class, () -> verificationService.validateSubmissionComplete(managementEntity, vpToken, presentationSubmissionDto));
    }

    @Test
    void testProcessDescriptionDoesNotMatchToken_thenThrowException() {
        String vpToken = "[\"VerifiablePresentation\"],\"verifiableCredential\": [{\"credentialSubject\": {\"first_name\": \"TestFirstname\",\"last_name\": \"TestLastName\",\"birthdate\": \"1949-01-22\"}}]}";

        ManagementEntity managementEntity = getManagementEntity(List.of("$.first_name", "$.last_name", "$.birthdate", "$.zip"));
        PresentationSubmissionDto presentationSubmissionDto = getPresentationDefinition(2, false);

        assertThrows(VerificationException.class, () -> verificationService.validateSubmissionComplete(managementEntity, vpToken, presentationSubmissionDto));
    }



    private ManagementEntity getManagementEntity(List<String> requiredFields) {
        Field field = Field.builder()
                .path(requiredFields)
                .build();

        Constraint constraint = Constraint.builder()
                .fields(List.of(field))
                .build();

        InputDescriptor inputDescriptor = InputDescriptor.builder()
                .constraints(List.of(constraint))
                .build();

        PresentationDefinition presentationDefinition = PresentationDefinition.builder()
                .id(UUID.randomUUID())
                .inputDescriptors(List.of(inputDescriptor))
                .build();

        return ManagementEntity.builder()
                .id(UUID.randomUUID())
                .state(VerificationStatusEnum.PENDING)
                .requestNonce("12")
                .requestedPresentation(presentationDefinition)
                .build();
    }

    private PresentationSubmissionDto getPresentationDefinition(final int numberOfDescriptors, final Boolean isList) {
        List<Descriptor> descriptorList = new ArrayList<>();
        for (int i = 0; i < numberOfDescriptors; i++) {
            descriptorList.add(Descriptor.builder()
                    .format("bbs")
                    .path(String.format("%s.verifiableCredential[%s].credentialSubject", isList ? "[0]" : "$", i))
                    .build());
        }

        return PresentationSubmissionDto.builder()
                .id(UUID.randomUUID())
                .descriptorMap(descriptorList)
                .build();

    }
}
