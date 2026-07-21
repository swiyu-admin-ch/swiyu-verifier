package ch.admin.bj.swiyu.verifier.service;

import ch.admin.bj.swiyu.verifier.common.config.CachingConfig;
import ch.admin.bj.swiyu.verifier.common.exception.ConfigurationException;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;
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

    /**
     * Returns a deep copy of the verifier metadata.
     * <p>
     * Do not use this function directly as you may alter the original metadata
     *
     * @return the verifier metadata
     * @throws ConfigurationException if the metadata cannot be read or copied
     */
    @Cacheable(CachingConfig.VERIFIER_METADATA_CACHE)
    public OpenidClientMetadataDto getVerifierMetadata() {
        try {
            var metadata = resourceToMappedData(clientMetadataResource, OpenidClientMetadataDto.class);
            validateMetadata(metadata);
            return metadata;
        } catch (IOException e) {
            throw new ConfigurationException("Cannot read verifier metadata", e);
        }
    }

    private <T> T resourceToMappedData(Resource res, Class<T> clazz) throws IOException {
        var json = res.getContentAsString(Charset.defaultCharset());
        return objectMapper.readValue(json, clazz);
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