package ch.admin.bit.eid.verifier_management.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
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
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.OAUTH2,
        flows = @OAuthFlows(clientCredentials  = @OAuthFlow(tokenUrl = "http://localhost:8180/mock_issuer/token?scope=scope1")))
public class OpenApiConfig {
}
