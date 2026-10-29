package ch.admin.bj.swiyu.verifier;

import ch.admin.bj.swiyu.verifier.infrastructure.web.management.VerifierManagementController;
import ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.VerificationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ApplicationIT extends AbstractMockMvcIT {

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

