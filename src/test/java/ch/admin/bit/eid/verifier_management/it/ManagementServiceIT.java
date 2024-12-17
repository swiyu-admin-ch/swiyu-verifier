package ch.admin.bit.eid.verifier_management.it;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.models.dto.CreateVerificationManagementDto;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;
import ch.admin.bit.eid.verifier_management.repositories.ManagementRepository;
import ch.admin.bit.eid.verifier_management.services.ManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "application.verification-ttl=4",
                "application.data-clear-interval=1"
        }
)
@ActiveProfiles("test")
public class ManagementServiceIT {
    @Autowired
    private ManagementRepository managementRepository;
    @Autowired
    private ManagementService managementService;

    @Test
    @Transactional
    public void deletionOfManagement_whenTtlExceeded_thenDeleted() {
        // GIVEN mgmt with expires at in the future
        var mgmt = managementService.createVerificationManagement(
                CreateVerificationManagementDto.builder()
                        .jwtSecuredAuthorizationRequest(true)
                        .presentationDefinition(PresentationDefinitionDto.builder()
                                .name("name")
                                .inputDescriptors(new ArrayList<>())
                                .format(new HashMap<>())
                                .purpose("purpose")
                                .build())
                        .build()
        );
        // GIVEN expired mgmt
        var mgmt2 = Management.builder()
                .id(UUID.randomUUID())
                .state(VerificationStatusEnum.PENDING)
                .expiresAt(System.currentTimeMillis() - 10000)
                .requestNonce("nonce")
                .jwtSecuredAuthorizationRequest(true).build();
        managementRepository.save(mgmt2);
        assertTrue(managementRepository.findById(mgmt.getId()).isPresent());
        assertTrue(managementRepository.findById(mgmt2.getId()).isPresent());
        // WHEN removing expired
        managementService.removeExpiredManagements();
        // THEN only non expired is present
        assertTrue(managementRepository.findById(mgmt.getId()).isPresent());
        assertFalse(managementRepository.findById(mgmt2.getId()).isPresent());
    }

    @Test
    public void deletionOfManagement_whenTtlNotExceeded_thenPresent() {
        var mgmt = managementService.createVerificationManagement(
                CreateVerificationManagementDto.builder()
                        .jwtSecuredAuthorizationRequest(true)
                        .presentationDefinition(PresentationDefinitionDto.builder()
                                .name("name")
                                .inputDescriptors(new ArrayList<>())
                                .format(new HashMap<>())
                                .purpose("purpose")
                                .build())
                        .build()
        );
        assertTrue(managementRepository.findById(mgmt.getId()).isPresent());
        managementService.removeExpiredManagements();
        assertTrue(managementRepository.findById(mgmt.getId()).isPresent());
    }
}
