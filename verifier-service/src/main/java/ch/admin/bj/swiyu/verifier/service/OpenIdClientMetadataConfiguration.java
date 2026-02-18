package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

@Configuration
@Data
@Slf4j
@RequiredArgsConstructor
public class OpenIdClientMetadataConfiguration {
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @NotNull
    @Value("${application.client-metadata-file:#{null}}")
    private Resource clientMetadataResource;

    private OpenidClientMetadataDto openIdClientMetadata;

    @PostConstruct
    public void initOpenIdClientMetadata() {
        final String resolvedJson = loadAndResolveTemplate();
        this.openIdClientMetadata = parseMetadata(resolvedJson);
        this.openIdClientMetadata.setVersion(applicationProperties.getMetadataVersion());
        validateMetadata(this.openIdClientMetadata);
    }

    private String loadAndResolveTemplate() {
        if (clientMetadataResource == null) {
            throw new IllegalStateException("Property 'application.client-metadata-file' must be configured");
        }

        final String template;
        try {
            template = clientMetadataResource.getContentAsString(Charset.defaultCharset());
        } catch (IOException exc) {
            log.error("Failed to load/read metadata file denoted by 'application.client-metadata-file': {}",
                    exc.getMessage());
            throw new IllegalStateException("Unable to load OpenID client metadata file", exc);
        }

        final Properties props = new Properties();
        props.setProperty("VERIFIER_DID", applicationProperties.getClientId());
        final PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");
        return helper.replacePlaceholders(template, props);
    }

    private OpenidClientMetadataDto parseMetadata(final String json) {
        try {
            return objectMapper.readValue(json, OpenidClientMetadataDto.class);
        } catch (JsonProcessingException exc) {
            log.error("Failed to deserialize DTO from JSON config OpenidClientMetadata : {}", exc.getMessage());
            throw new IllegalStateException("Unable to parse OpenID client metadata JSON", exc);
        }
    }

    private void validateMetadata(final OpenidClientMetadataDto metadata) {
        final Set<ConstraintViolation<OpenidClientMetadataDto>> violations = validator.validate(metadata);
        if (violations.isEmpty()) {
            return;
        }

        // CAUTION The ConstraintViolation object's getPropertyPath() getter
        //         would simply return a camel-cased name of the underlying DTO's Java class field,
        //         which might not really be useful,
        //         as actually preferred here is the value denoted by the
        //         DTO class field's @JsonProperty annotation.
        //         Assuming this value (JSON property) is always a snake-cased equivalent
        //         of DTO's Java class field name,
        //         we could just simply employ a proper case-conversion helper.
        final String message = "Invalid OpenID client metadata: " + violations.stream()
                .map(v -> "'" + camelToSnakeCase(v.getPropertyPath().toString()) + "' " + v.getMessage())
                .collect(java.util.stream.Collectors.joining(", "));

        log.error(message);
        throw new IllegalStateException(message);
    }

    /**
     * A regex-driven helper to convert one case into another.
     *
     * @param camelCaseString to convert. Camel-case assumed.
     * @return converted snake-case string
     */
    private static String camelToSnakeCase(final String camelCaseString) {
        return camelCaseString
                .replaceAll("([A-Z])(?=[A-Z])", "$1_")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT); // although equivalent to toLowerCase(), it makes PMD way much happier
    }
}