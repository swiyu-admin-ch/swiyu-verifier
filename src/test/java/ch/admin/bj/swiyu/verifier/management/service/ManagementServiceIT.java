package ch.admin.bj.swiyu.verifier.management.service;

import ch.admin.bj.swiyu.verifier.management.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.management.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.management.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.management.domain.management.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ApiFixtures.createVerificationManagementDto;
import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ManagementFixtures.management;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@Import({ManagementService.class, ApplicationProperties.class})
@DataJpaTest
@ActiveProfiles("test")
public class ManagementServiceIT {

    @Autowired
    private ManagementRepository managementRepository;
    @Autowired
    private ManagementService managementService;


    @BeforeEach
    void setUp() {
    }

    @Test
    public void createVerificationManagementTest() {
        // GIVEN
        var request = createVerificationManagementDto();
        // WHEN
        var result = managementService.createVerificationManagement(request);
        // THEN
        var management = managementRepository.findById(result.getId()).orElseThrow();
        assertThat(management.getId()).isNotNull();
        assertThat(management.getRequestNonce()).isNotBlank();
        assertThat(management.getState()).isEqualTo(VerificationStatus.PENDING);
        assertThat(management.getJwtSecuredAuthorizationRequest()).isEqualTo(request.getJwtSecuredAuthorizationRequest());
        assertThat(management.getExpirationInSeconds()).isEqualTo(900);
        assertThat(management.getWalletResponse()).isNull();
    }

    @Test
    public void removeExpiredManagements_whenTtlExceeded_thenDeleted() {
        // GIVEN valid and expired management
        var mgmtValid = managementRepository.save(management(900));
        var mgmtExpired = managementRepository.saveAndFlush(management(-100));
        assertTrue(managementRepository.findById(mgmtValid.getId()).isPresent());
        assertTrue(managementRepository.findById(mgmtExpired.getId()).isPresent());

        // WHEN removing expired
        managementService.removeExpiredManagements();
        // THEN only non expired is present
        assertTrue(managementRepository.findById(mgmtValid.getId()).isPresent());
        assertFalse(managementRepository.findById(mgmtExpired.getId()).isPresent());
    }

    @Test
    public void removeExpiredManagements_whenTtlNotExceeded_thenPresent() {
        // GIVEN
        var mgmtValid = managementRepository.save(management(900));
        assertTrue(managementRepository.findById(mgmtValid.getId()).isPresent());
        // WHEN
        managementService.removeExpiredManagements();
        // THEN
        assertTrue(managementRepository.findById(mgmtValid.getId()).isPresent());
    }

    @Test
    void getManagement_ShouldReturnManagement_WhenFound() {
        // GIVEN
        var management = management();
        managementRepository.saveAndFlush(management);
        // WHEN
        var result = managementService.getManagement(management.getId());
        // THEN
        assertEquals(management.getId(), result.getId());
    }

    @Test
    void getManagement_ShouldThrowException() {
        assertThrows(VerificationNotFoundException.class, () -> managementService.getManagement(UUID.randomUUID()));
    }

    @Test
    void getManagement_ShouldDeleteEntryAfterPending() {
        // GIVEN
        var expiredManagement = managementRepository.save(management(-100));
        // WHEN
        managementService.getManagement(expiredManagement.getId());
        // THEN
        assertThrows(VerificationNotFoundException.class, () -> managementService.getManagement(expiredManagement.getId()));


    }

    @Test
    void createVerificationManagement_WithNullRequestNonce_thenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> managementService.createVerificationManagement(null));
    }

    @Test
    void createVerificationManagement_WithNullPresentation_thenIllegalArgumentException() {
        // GIVEN
        var nullPresentationInRequest = createVerificationManagementDto(null);
        // WHEN / THEN
        assertThrows(IllegalArgumentException.class, () -> managementService.createVerificationManagement(nullPresentationInRequest));
    }
}
