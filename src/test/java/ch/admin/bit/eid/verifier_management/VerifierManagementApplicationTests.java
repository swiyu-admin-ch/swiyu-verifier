package ch.admin.bit.eid.verifier_management;

import ch.admin.bit.eid.verifier_management.controllers.VerifierManagementController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class VerifierManagementApplicationTests {

	@Autowired
	private VerifierManagementController verifierManagementController;

	@Test
	void contextLoads() {
		assertThat(verifierManagementController).isNotNull();
	}

}
