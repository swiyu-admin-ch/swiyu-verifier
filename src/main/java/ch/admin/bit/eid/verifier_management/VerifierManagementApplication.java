package ch.admin.bit.eid.verifier_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class VerifierManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(VerifierManagementApplication.class, args);
	}

}
