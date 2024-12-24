package ch.admin.bj.swiyu.verifier.management.infrastructure.web.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Verifier management service",
                description = "Generic Verifier management service"
        )
)
public class OpenApiConfig { }
