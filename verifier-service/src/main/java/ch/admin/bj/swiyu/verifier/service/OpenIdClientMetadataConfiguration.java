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
    @SuppressWarnings({"PMD.CyclomaticComplexity"})
    public void initOpenIdClientMetadata() {

        final String template;
        try {
            template = clientMetadataResource.getContentAsString(Charset.defaultCharset());
        } catch (IOException exc) {

            if (log.isErrorEnabled())
                log.error("⚠️ {} {}", "Failed to load/read metadata file denoted by the 'application.client-metadata-file' property due to:", exc.getMessage());

            throw new IllegalStateException(exc);
        }

        // find and set CLIENT_ID in the template
        final Properties prop = new Properties();
        prop.setProperty("VERIFIER_DID", applicationProperties.getClientId());
        final PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");
        final String loadedTemplate = helper.replacePlaceholders(template, prop);

        try {
            openIdClientMetadata = objectMapper.readValue(loadedTemplate, OpenidClientMetadataDto.class);
        } catch (JsonProcessingException exc) {

            if (log.isErrorEnabled())
                log.error("⚠️ {} '{}' due to: {}", "Failed to deserialize DTO from JSON config", loadedTemplate, exc.getMessage());

            throw new IllegalStateException(exc);
        }

        openIdClientMetadata.setVersion(applicationProperties.getMetadataVersion());

        final Set<ConstraintViolation<OpenidClientMetadataDto>> violations = validator.validate(openIdClientMetadata);

        if (!violations.isEmpty()) {
            final StringBuilder strBuilder = new StringBuilder("Invalid OpenID client metadata: ");
            strBuilder.append(violations.stream()
                    // CAUTION The ConstraintViolation object's getPropertyPath() getter
                    //         would simply return a camel-cased name of the underlying DTO's Java class field,
                    //         which might not really be useful,
                    //         as actually preferred here is the value denoted by the
                    //         DTO class field's @JsonProperty annotation.
                    //         Assuming this value (JSON property) is always a snake-cased equivalent
                    //         of DTO's Java class field name,
                    //         we could just simply employ a proper case-conversion helper.
                    .map(v -> "'" + camelToSnakeCase(v.getPropertyPath().toString()) + "' " + v.getMessage())
                    .collect(java.util.stream.Collectors.joining(", ")));

            if (log.isErrorEnabled()) log.error("⚠️ {}", strBuilder);

            throw new IllegalStateException(strBuilder.toString());
        }
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
                .toLowerCase(Locale.getDefault()); // although equivalent to toLowerCase(), it makes PMD way much happier
    }
}