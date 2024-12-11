package ch.admin.bit.eid.verifier_management.it;

import ch.admin.bit.eid.verifier_management.models.dto.CreateVerificationManagementDto;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionDto;
import ch.admin.bit.eid.verifier_management.repositories.ManagementRepository;
import ch.admin.bit.eid.verifier_management.services.ManagementService;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "application.verification-ttl=4",
                "application.data-clear-interval=1"
        }
)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "5m")
public class SchedulerServiceIT {
    @Autowired
    private ManagementRepository managementRepository;
    @Autowired
    private ManagementService managementService;

    @Test
    public void deletionOfManagement_whenTtlExceeded_thenDeleted() throws InterruptedException {
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
        // Timeout above the ttl
        TimeUnit.MILLISECONDS.sleep(5000);
        assertFalse(managementRepository.findById(mgmt.getId()).isPresent());
    }

    @Test
    public void deletionOfManagement_whenTtlNotExceeded_thenPresent() throws InterruptedException {
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
        // Timeout below the ttl
        TimeUnit.MILLISECONDS.sleep(1000);
        assertTrue(managementRepository.findById(mgmt.getId()).isPresent());
    }
}
