/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.api.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OpenIdClientMetadataConfiguration {
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @NotNull
    @Value("${application.client-metadata-file}")
    private Resource clientMetadataResource;

    private OpenidClientMetadataDto openIdClientMetadata;

    @PostConstruct
    public void initOpenIdClientMetadata() throws IOException {
        var template = clientMetadataResource.getContentAsString(Charset.defaultCharset());

        // find and set CLIENT_ID in the template
        Properties prop = new Properties();
        prop.setProperty("VERIFIER_DID", applicationProperties.getClientId());
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");
        var loadedTemplate = helper.replacePlaceholders(template, prop);

        openIdClientMetadata = objectMapper.readValue(loadedTemplate, OpenidClientMetadataDto.class);
        openIdClientMetadata.setVersion(applicationProperties.getMetadataVersion());

        Set<ConstraintViolation<OpenidClientMetadataDto>> violations = validator.validate(openIdClientMetadata);

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Invalid OpenID client metadata: ");
            for (ConstraintViolation<OpenidClientMetadataDto> violation : violations) {
                sb.append(violation.getMessage()).append(", ");
            }
            throw new IllegalStateException(sb.toString());
        }
    }
}