package ch.admin.bj.swiyu.verifier.management;

import ch.admin.bj.swiyu.verifier.management.infrastructure.web.controller.VerifierManagementController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureTestDatabase(connection = EmbeddedDatabaseConnection.H2)
class VerifierManagementApplicationIT {

    @Autowired
    private VerifierManagementController verifierManagementController;

    @Test
    void contextLoads() {
        assertThat(verifierManagementController).isNotNull();
    }
}
