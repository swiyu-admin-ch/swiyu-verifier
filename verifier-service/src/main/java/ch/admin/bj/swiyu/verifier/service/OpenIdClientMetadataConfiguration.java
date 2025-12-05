/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.api.metadata.OpenidClientMetadataDto;
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

        String template;
        try {
            template = clientMetadataResource.getContentAsString(Charset.defaultCharset());
        } catch (IOException exc) {

            log.error("⚠️ {} {}", "Failed to load/read metadata file denoted by the 'application.client-metadata-file' property due to:", exc.getMessage());

            throw new IllegalStateException(exc);
        }

        // find and set CLIENT_ID in the template
        Properties prop = new Properties();
        prop.setProperty("VERIFIER_DID", applicationProperties.getClientId());
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");
        var loadedTemplate = helper.replacePlaceholders(template, prop);

        try {
            openIdClientMetadata = objectMapper.readValue(loadedTemplate, OpenidClientMetadataDto.class);
        } catch (JsonProcessingException exc) {
            log.error("⚠️ {} '{}' due to: {}", "Failed to deserialize DTO from JSON config", loadedTemplate, exc.getMessage());

            throw new IllegalStateException(exc);
        }

        openIdClientMetadata.setVersion(applicationProperties.getMetadataVersion());

        Set<ConstraintViolation<OpenidClientMetadataDto>> violations = validator.validate(openIdClientMetadata);

        if (!violations.isEmpty()) {
            var sb = new StringBuilder("Invalid OpenID client metadata: ");
            sb.append(violations.stream()
                    // CAUTION The ConstraintViolation object's getPropertyPath() getter would simply return a camel-cased name of
                    //         the underlying DTO's Java class field, which might not really be useful,
                    //         as actually preferred here is the value denoted by the DTO class field's @JsonProperty annotation.
                    //         Assuming this value (JSON property) is always a snake-cased equivalent of DTO's Java class field name,
                    //         we could just simply employ a proper case-conversion helper.
                    .map(v -> "'" + camelToSnakeCase(v.getPropertyPath().toString()) + "' " + v.getMessage())
                    .collect(java.util.stream.Collectors.joining(", ")));

            log.error("⚠️ {}", sb);

            throw new IllegalStateException(sb.toString());
        }
    }

    /**
     * A regex-driven helper to convert one case into another.
     *
     * @param camelCaseString to convert. Camel-case assumed.
     * @return converted snake-case string
     */
    private static String camelToSnakeCase(String camelCaseString) {
        return camelCaseString
                .replaceAll("([A-Z])(?=[A-Z])", "$1_")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }
}