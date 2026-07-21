
package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ConfigurationException;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenIdClientMetadataConfigurationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Resource clientMetadataResource;
    private OpenIdClientMetadataConfiguration config;

    @BeforeEach
    void setUp() {
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        clientMetadataResource = mock(Resource.class);

        config = new OpenIdClientMetadataConfiguration(applicationProperties, objectMapper, validator);
        config.setClientMetadataResource(clientMetadataResource);

        String clientId = "test-client-id";
        when(applicationProperties.getClientId()).thenReturn(clientId);
        String clientIdWithPrefix = "prefix:test-client-id";
        when(applicationProperties.getClientIdWithPrefix()).thenReturn(clientIdWithPrefix);
    }

    @Test
    void getVerifierMetadata_withValidMetadata_setsMetadata() throws IOException {
        String template = "{\"client_id\":\"${VERIFIER_DID}\",\"logo\":\"logo\",\"vp_formats\":{\"jwt_vp\":{\"alg\":[\"ES256\"]}}}";

        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        var metadata = config.getVerifierMetadata();

        assertThat(metadata.getAdditionalProperties())
                .containsEntry("logo", "logo");
    }

    @Test
    void getVerifierMetadata_invalidMetadataFile_throwsException() throws IOException {
        var ioExcMsg = "Cannot read verifier metadata";
        when(clientMetadataResource.getContentAsString(Charset.defaultCharset()))
                .thenThrow(new IOException(ioExcMsg));

        var exc = assertThrowsExactly(ConfigurationException.class, config::getVerifierMetadata);

        assertThat(exc)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining(ioExcMsg);
    }

    @ParameterizedTest
    @MethodSource("invalidMetadataProvider")
    void getVerifierMetadata_invalidCases_throwsException(String template) throws IOException {

        var expectedMessage = "Cannot read verifier metadata";
        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        var exception = assertThrows(ConfigurationException.class, () -> config.getVerifierMetadata());

        assertThat(exception)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidMetadataProvider() {
        return Stream.of(
                Arguments.of(
                        ""
                ),
                Arguments.of(
                        "[]"
                )
        );
    }
}
