package ch.admin.bit.eid.oid4vp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class Oid4vpApplication {

	public static void main(String[] args) {
		SpringApplication.run(Oid4vpApplication.class, args);
	}

}
