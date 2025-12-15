
package ch.admin.bj.swiyu.verifier.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
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
    private final String clientId = "test-client-id";
    private final String metadataVersion = "1.0.0";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Resource clientMetadataResource;
    private OpenIdClientMetadataConfiguration config;

    @BeforeEach
    void setUp() {
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        clientMetadataResource = mock(Resource.class);

        config = new OpenIdClientMetadataConfiguration(applicationProperties, objectMapper, validator);
        config.setClientMetadataResource(clientMetadataResource);

        when(applicationProperties.getClientId()).thenReturn(clientId);
        when(applicationProperties.getMetadataVersion()).thenReturn(metadataVersion);
    }

    @Test
    void initOpenIdClientMetadata_withValidMetadata_setsMetadata() throws IOException {
        String template = "{\"client_id\":\"${VERIFIER_DID}\",\"logo\":\"logo\",\"vp_formats\":{\"jwt_vp\":{\"alg\":[\"ES256\"]}}}";

        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        config.initOpenIdClientMetadata();

        var metadata = config.getOpenIdClientMetadata();

        assertThat(metadata.getClientId()).isEqualTo(clientId);
        assertThat(metadata.getVersion()).isEqualTo(metadataVersion);
        assertThat(metadata.getVpFormats().jwtVerifiablePresentation().algorithms())
                .first()
                .isEqualTo("ES256");
        assertThat(metadata.getAdditionalProperties())
                .containsEntry("logo", "logo");
    }

    @Test
    void initOpenIdClientMetadata_withoutClientIdAndVpFormats_throwsException() throws IOException {
        String template = "{\"other\":\"value\"}";

        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        var exception = assertThrows(IllegalStateException.class, config::initOpenIdClientMetadata);

        assertThat(exception)
                .hasMessageContaining("Invalid OpenID client metadata")
                .hasMessageContaining("'client_id' must not be blank")
                .hasMessageContaining("'vp_formats' must not be null");
    }

    @Test
    void initOpenIdClientMetadata_invalidMetadataFile_throwsException() throws IOException {
        var ioExcMsg = "Unable to load OpenID client metadata file";
        when(clientMetadataResource.getContentAsString(Charset.defaultCharset()))
                .thenThrow(new IOException(ioExcMsg));

        var exc = assertThrowsExactly(IllegalStateException.class, config::initOpenIdClientMetadata);

        assertThat(exc)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ioExcMsg);
    }

    @ParameterizedTest
    @MethodSource("invalidMetadataProvider")
    void initOpenIdClientMetadata_invalidCases_throwsException(String template, String expectedMessage) throws IOException {
        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        var exception = assertThrows(IllegalStateException.class, config::initOpenIdClientMetadata);

        assertThat(exception)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidMetadataProvider() {
        return Stream.of(
                Arguments.of(
                        "",
                        "Unable to parse OpenID client metadata JSON"
                ),
                Arguments.of(
                        "[]",
                        "Unable to parse OpenID client metadata JSON"
                ),
                Arguments.of(
                        "{\"client_id\":\"${VERIFIER_DID}\",\"other\":\"value\"}",
                        "'vp_formats' must not be null"
                ),
                Arguments.of(
                        "{\"client_id\":\"${VERIFIER_DID}\",\"other\":\"value\",\"vp_formats\":{}}",
                        "'vp_formats.jwt_verifiable_presentation' must not be null"
                ),
                Arguments.of(
                        "{\"client_id\":\"${VERIFIER_DID}\",\"other\":\"value\",\"vp_formats\":{\"jwt_vp\":{}}}",
                        "'vp_formats.jwt_verifiable_presentation.algorithms' must not be empty"
                ),
                Arguments.of(
                        "{\"client_id\":\"${VERIFIER_DID}\",\"other\":\"value\",\"vp_formats\":{\"jwt_vp\":{\"alg\":[]}}}",
                        "'vp_formats.jwt_verifiable_presentation.algorithms' must not be empty"
                ),
                Arguments.of(
                        "{\"client_id\":\"${VERIFIER_DID}\",\"other\":\"value\",\"vp_formats\":{\"jwt_vp\":{\"alg\":[\"ES384\"]}}}",
                        "'vp_formats.jwt_verifiable_presentation.algorithms' Invalid jwt values provided. Only ES256 is supported"
                )
        );
    }
}
