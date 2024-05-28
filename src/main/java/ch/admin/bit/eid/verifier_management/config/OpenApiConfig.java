package ch.admin.bit.eid.verifier_management.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Issuer management service",
                description = "Generic Issuer management service",
                contact = @Contact(
                        email = "eid@bit.admin.ch",
                        name = "eID",
                        url = "https://confluence.eap.bit.admin.ch/display/YOUR_TEAM/"
                )
        )
)
public class OpenApiConfig {
}
