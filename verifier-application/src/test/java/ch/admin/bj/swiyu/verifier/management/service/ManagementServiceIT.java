/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.service;


import ch.admin.bj.swiyu.verifier.api.VerificationErrorResponseCodeDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseData;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.service.management.ManagementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ApiFixtures.createVerificationManagementDto;
import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ManagementFixtures.management;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({ManagementService.class, ApplicationProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // we don't want the tests to start in a transaction by default
class ManagementServiceIT {

    @Autowired
    private ManagementRepository managementRepository;
    @Autowired
    private ManagementService managementService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transaction;

    @Test
    void createVerificationManagementTest() {
        // GIVEN
        var request = createVerificationManagementDto(List.of("did:example:123", "did:example:456"));
        // WHEN
        var result = managementService.createVerificationManagement(request);
        // THEN
        var management = managementRepository.findById(result.id()).orElseThrow();
        assertThat(management.getId()).isNotNull();
        assertThat(management.getRequestNonce()).isNotBlank();
        assertThat(management.getState()).isEqualTo(VerificationStatus.PENDING);
        assertThat(management.getJwtSecuredAuthorizationRequest()).isEqualTo(request.jwtSecuredAuthorizationRequest());
        assertThat(management.getExpirationInSeconds()).isEqualTo(900);
        assertThat(management.getWalletResponse()).isNull();
        // check accepted issuer dids are correctly stored and retrieved
        assertThat(management.getAcceptedIssuerDids().size()).isEqualTo(2);
        assertThat(management.getAcceptedIssuerDids().contains("did:example:123")).isTrue();
        assertThat(management.getAcceptedIssuerDids().contains("did:example:456")).isTrue();
    }

    @Test
    void createVerificationManagementTest_acceptedIssuersEmpty() {
        // GIVEN
        var request = createVerificationManagementDto(List.of());
        // WHEN
        var result = managementService.createVerificationManagement(request);
        // THEN
        var management = managementRepository.findById(result.id()).orElseThrow();
        assertThat(management.getId()).isNotNull();
        assertThat(management.getRequestNonce()).isNotBlank();
        assertThat(management.getState()).isEqualTo(VerificationStatus.PENDING);
        assertThat(management.getJwtSecuredAuthorizationRequest()).isEqualTo(request.jwtSecuredAuthorizationRequest());
        assertThat(management.getExpirationInSeconds()).isEqualTo(900);
        assertThat(management.getWalletResponse()).isNull();
        // check accepted issuer dids list is empty
        assertThat(management.getAcceptedIssuerDids()).isEmpty();
    }

    @Test
    void createVerificationManagementTest_acceptedIssuersNull() {
        // GIVEN
        var request = createVerificationManagementDto(null);
        // WHEN
        var result = managementService.createVerificationManagement(request);
        // THEN
        var management = managementRepository.findById(result.id()).orElseThrow();
        assertThat(management.getId()).isNotNull();
        assertThat(management.getRequestNonce()).isNotBlank();
        assertThat(management.getState()).isEqualTo(VerificationStatus.PENDING);
        assertThat(management.getJwtSecuredAuthorizationRequest()).isEqualTo(request.jwtSecuredAuthorizationRequest());
        assertThat(management.getExpirationInSeconds()).isEqualTo(900);
        assertThat(management.getWalletResponse()).isNull();
        // check accepted issuer dids list is empty
        assertThat(management.getAcceptedIssuerDids()).isEmpty();
    }

    @Test
    void removeExpiredManagements_whenTtlExceeded_thenDeleted() {
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
    void removeExpiredManagements_whenTtlNotExceeded_thenPresent() {
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
        assertEquals(management.getId(), result.id());
    }

    @Test
    void getManagement_ShouldThrowException() {
        var id = UUID.randomUUID();
        assertThrows(VerificationNotFoundException.class, () -> managementService.getManagement(id));
    }

    @Test
    void getManagement_ShouldDeleteEntryAfterPending() {
        // GIVEN
        var id = managementRepository.save(management(-100)).getId();
        // WHEN
        managementService.getManagement(id);
        // THEN
        assertThrows(VerificationNotFoundException.class, () -> managementService.getManagement(id));
    }

    @Test
    void getManagement_ShouldReturnManagement_WithResponseData() {
        // GIVEN
        var management = management();
        managementRepository.saveAndFlush(management);
        // imitate the behaviour of the OID4VP application and mark verification as failed
        imitateVerificationFailed(management.getId());

        // WHEN
        var result = managementService.getManagement(management.getId());
        // THEN
        assertEquals(management.getId(), result.id());
        assertEquals(VerificationErrorResponseCodeDto.CREDENTIAL_INVALID, result.walletResponse().errorCode());
        assertEquals("value", result.walletResponse().credentialSubjectData().get("key"));
        assertEquals("Not Found", result.walletResponse().errorDescription());
    }

    @Test
    void createVerificationManagement_WithNullRequestNonce_thenIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> managementService.createVerificationManagement(null));
    }

    @Test
    void createVerificationManagement_WithNullPresentation_thenIllegalArgumentException() {
        // GIVEN
        var nullPresentationInRequest = createVerificationManagementDto(null, null);
        // WHEN / THEN
        assertThrows(IllegalArgumentException.class, () -> managementService.createVerificationManagement(nullPresentationInRequest));
    }

    /**
     * Sets the state to FAILED and updates the walletResponse as the OID4VP application would do.
     */
    private void imitateVerificationFailed(UUID id) {
        transaction.executeWithoutResult(status -> {
            entityManager.createQuery("UPDATE Management m SET m.state = :state, m.walletResponse = :walletResponse WHERE m.id = :id")
                    .setParameter("id", id)
                    .setParameter("state", VerificationStatus.FAILED)
                    .setParameter("walletResponse", new ResponseData(
                            VerificationErrorResponseCode.CREDENTIAL_INVALID,
                            "Not Found",
                            "{\"key\":\"value\"}"
                    ))
                    .executeUpdate();
        });
    }
}
