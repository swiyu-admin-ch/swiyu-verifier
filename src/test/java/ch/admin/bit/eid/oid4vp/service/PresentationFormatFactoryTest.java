package ch.admin.bit.eid.oid4vp.service;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfig;
import ch.admin.bit.eid.oid4vp.config.SDJWTConfig;
import ch.admin.bit.eid.oid4vp.model.PresentationFormatFactory;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static ch.admin.bit.eid.oid4vp.mock.CredentialSubmissionMock.getPresentationDefinitionMockWithFormat;
import static ch.admin.bit.eid.oid4vp.mock.ManagementEntityMock.getManagementEntityMock;
import static ch.admin.bit.eid.oid4vp.mock.PresentationDefinitionMocks.createPresentationDefinitionMock;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
class PresentationFormatFactoryTest {

    private final UUID requestId = UUID.randomUUID();
    private final String vpToken = """
            [{
            "type": ["VerifiablePresentation"],
            "verifiableCredential": [{"credentialSubject": {"first_name": "TestFirstname","last_name": "TestLastName","birthdate": "1949-01-22"}}]}
            ]
            """;
    @MockBean
    private VerificationManagementRepository verificationManagementRepository;
    @MockBean
    private BBSKeyConfig bbsKeyConfiguration;
    @MockBean
    private SDJWTConfig sdjwtConfiguration;
    private PresentationFormatFactory presentationFormatFactory;
    private ManagementEntity managementEntity;

    @BeforeEach
    void setUp() {
        presentationFormatFactory = new PresentationFormatFactory(bbsKeyConfiguration, sdjwtConfiguration);
        PresentationDefinition presentationDefinition = createPresentationDefinitionMock(requestId, List.of("$.first_name", "$.last_name", "$.birthdate"));
        managementEntity = getManagementEntityMock(requestId, presentationDefinition);
    }


    @Test
    void testWrongFormat_ThenException() {
        PresentationSubmission presentationSubmission = getPresentationDefinitionMockWithFormat(1, true, "wrong-format");
        assertThrows(IllegalArgumentException.class, () -> presentationFormatFactory
                .getFormatBuilder(vpToken, managementEntity, presentationSubmission, verificationManagementRepository));
    }

    @Test
    void testWNoFormat_ThenException() {
        PresentationSubmission presentationSubmission = getPresentationDefinitionMockWithFormat(1, true, null);

        assertThrows(IllegalArgumentException.class, () -> presentationFormatFactory
                .getFormatBuilder(vpToken, managementEntity, presentationSubmission, verificationManagementRepository));
    }
}
