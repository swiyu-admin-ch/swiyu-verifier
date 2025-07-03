package ch.admin.bj.swiyu.verifier.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenIdClientMetadataConfigurationIT {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final String clientId = "test-client-id";
    private final String metadataVersion = "1.0.0";

    private ObjectMapper objectMapper = new ObjectMapper();

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
    void testInitOpenIdClientMetadataWithValidMetadata_thenSuccess() throws Exception {
        String template = "{\"client_id\":\"${VERIFIER_DID}\",\"logo\":\"logo\",\"vp_formats\":{\"jwt_vp\":{\"alg\":[\"ES256\"]}}}";

        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        config.initOpenIdClientMetadata();

        var metadata = config.getOpenIdClientMetadata();

        assertEquals(metadata.getClientId(), clientId);
        assertEquals(metadata.getVersion(), metadataVersion);
        assertEquals("ES256", metadata.getVpFormats().jwtVerifiablePresentation().algorithms().getFirst());
        assertEquals("logo", metadata.getAdditionalProperties().get("logo"));
    }

    @Test
    void testInitOpenIdClientMetadataNoClientIdAndVpFormats_throwsException() throws Exception {
        String template = "{\"other\":\"value\"}";

        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        var exception = assertThrows(IllegalStateException.class, () -> {
            config.initOpenIdClientMetadata();
        });

        assertTrue(exception.getMessage().contains("Invalid OpenID client metadata"));
        assertTrue(exception.getMessage().contains("clientId must not be blank"));
        assertTrue(exception.getMessage().contains("vpFormats must not be null"));
    }

    @Test
    void testInitOpenIdClientMetadataInvalidVpFormats_throwsException() throws Exception {
        String template = "{\"client_id\":\"${VERIFIER_DID}\",\"other\":\"value\",\"vp_formats\":{}}";

        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        var exception = assertThrows(IllegalStateException.class, () -> {
            config.initOpenIdClientMetadata();
        });

        assertTrue(exception.getMessage().contains("jwtVerifiablePresentation must not be null"));
    }

    @Test
    void testInitOpenIdClientMetadataInvalidJwtVp_throwsException() throws Exception {
        String template = "{\"client_id\":\"${VERIFIER_DID}\",\"other\":\"value\",\"vp_formats\":{\"jwt_vp\":{}}}";

        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        var exception = assertThrows(IllegalStateException.class, () -> {
            config.initOpenIdClientMetadata();
        });

        assertTrue(exception.getMessage().contains("vpFormats.jwtVerifiablePresentation.algorithms must not be empty"));
    }

    @Test
    void testInitOpenIdClientMetadataEmptyAlgs_throwsException() throws Exception {
        String template = "{\"client_id\":\"${VERIFIER_DID}\",\"other\":\"value\",\"vp_formats\":{\"jwt_vp\":{\"alg\":[]}}}";

        when(clientMetadataResource.getContentAsString(Charset.defaultCharset())).thenReturn(template);

        var exception = assertThrows(IllegalStateException.class, () -> {
            config.initOpenIdClientMetadata();
        });

        assertTrue(exception.getMessage().contains("vpFormats.jwtVerifiablePresentation.algorithms must not be empty"));
    }
}