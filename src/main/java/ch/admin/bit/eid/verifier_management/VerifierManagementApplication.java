package ch.admin.bit.eid.verifier_management;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

@SpringBootApplication
@EnableConfigurationProperties
@Slf4j
public class VerifierManagementApplication {

	public static void main(String[] args) {
		Environment env = SpringApplication.run(VerifierManagementApplication.class, args).getEnvironment();
		String appName = env.getProperty("spring.application.name");
		String serverPort = env.getProperty("server.port");
		log.info(
				"""
                        
                        ----------------------------------------------------------------------------
                        \t'{}' is running!\s
                        \tProfile(s): \t\t\t\t{}
                        \tSwaggerUI:   \t\t\t\thttp://localhost:{}/swagger-ui.html
                        ----------------------------------------------------------------------------""",
				appName,
				env.getActiveProfiles(),
				serverPort
		);
	}

}
