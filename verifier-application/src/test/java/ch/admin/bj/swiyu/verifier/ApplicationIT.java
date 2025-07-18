/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier;

import ch.admin.bj.swiyu.verifier.infrastructure.web.management.VerifierManagementController;
import ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.VerificationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Transactional
class ApplicationIT {

    @Autowired
    private VerificationController verificationController;

    @Autowired
    private VerifierManagementController verifierManagementController;

    /*Sanity Test to check if the application even loads*/
    @Test
    void contextLoadsVerification() {
        assertThat(verificationController).isNotNull();
    }

    @Test
    void contextLoadsVerifierManagement() {
        assertThat(verifierManagementController).isNotNull();
    }
}

