package ch.admin.bj.swiyu.verifier.infrastructure.config;

import ch.admin.bj.swiyu.verifier.dto.callback.WebhookCallbackDto;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.AllArgsConstructor;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AllArgsConstructor
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Verifier API")
                .description("Generic Verifier service"));
    }

    @Bean
    GroupedOpenApi api() {
        return GroupedOpenApi.builder()
                .group("API")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public GlobalOpenApiCustomizer openApiCustomizer() {
        return openApi -> openApi.getComponents().getSchemas().put("WebhookCallback",
                ModelConverters.getInstance().readAllAsResolvedSchema(WebhookCallbackDto.class).schema);
    }
}