package ch.admin.bj.swiyu.verifier.service.oid4vp;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseModeType;
import ch.admin.bj.swiyu.verifier.domain.management.ResponseSpecification;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenIdClientMetadataVpFormatSdJwt;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenIdClientMetadataVpFormatsSupported;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataServiceTest {
    private OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;
    private MetadataService metadataService;
    private OpenidClientMetadataDto baseMetadata;

    @BeforeEach
    void setUp() {
        openIdClientMetadataConfiguration = mock(OpenIdClientMetadataConfiguration.class);
        metadataService = new MetadataService(openIdClientMetadataConfiguration);
        // Create base metadata with default values
        OpenIdClientMetadataVpFormatSdJwt sdJwtFormat = new OpenIdClientMetadataVpFormatSdJwt(
                List.of("ES256"),
                List.of("ES256")
        );
        baseMetadata = OpenidClientMetadataDto.builder()
                .vpFormatsSupported(new OpenIdClientMetadataVpFormatsSupported(sdJwtFormat))
                .encryptedResponseEncValuesSupported(List.of("A256GCM"))
                .additionalProperties(new HashMap<>())
                .build();
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_withDirectPostResponseMode_doesNotAddJwks() {
        // Given
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST)
                .jwks("{\"keys\":[]}")
                .encryptedResponseEncValuesSupported(List.of("A256GCM", "A128GCM"))
                .build();
        Management management = createManagement(null, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(baseMetadata);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJwks()).isNull();
        assertThat(result.getEncryptedResponseEncValuesSupported()).containsExactly("A256GCM");
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_withDirectPostJwtResponseMode_addsJwksAndEncryptionValues() {
        // Given
        String jwksJson = "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"test\",\"y\":\"test\"}]}";
        List<String> encryptionValues = List.of("A256GCM", "A128GCM");
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST_JWT)
                .jwks(jwksJson)
                .encryptedResponseEncValuesSupported(encryptionValues)
                .build();
        Management management = createManagement(null, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(baseMetadata);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJwks()).isNotNull();
        assertThat(result.getEncryptedResponseEncValuesSupported()).containsExactlyElementsOf(encryptionValues);
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_withNullOverrides_returnsUnmodifiedMetadata() {
        // Given
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST)
                .build();
        Management management = createManagement(null, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(baseMetadata);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAdditionalProperties()).isEmpty();
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_withEmptyOverrides_returnsUnmodifiedMetadata() {
        // Given
        Map<String, String> emptyOverrides = new HashMap<>();
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST)
                .build();
        Management management = createManagement(emptyOverrides, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(baseMetadata);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAdditionalProperties()).isEmpty();
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_withClientMetadataOverrides_mergesOverridesIntoAdditionalProperties() {
        // Given
        Map<String, String> overrides = new HashMap<>();
        overrides.put("client_name", "Custom Verifier");
        overrides.put("logo_uri", "https://example.com/logo.png");
        overrides.put("client_name#en", "Custom Verifier EN");
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST)
                .build();
        Management management = createManagement(overrides, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(baseMetadata);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAdditionalProperties())
                .hasSize(3)
                .containsEntry("client_name", "Custom Verifier")
                .containsEntry("logo_uri", "https://example.com/logo.png")
                .containsEntry("client_name#en", "Custom Verifier EN");
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_withOverridesAndExistingAdditionalProperties_mergesBothCorrectly() {
        // Given
        Map<String, Object> existingAdditionalProps = new HashMap<>();
        existingAdditionalProps.put("existing_key", "existing_value");
        existingAdditionalProps.put("client_name", "Original Name");
        OpenIdClientMetadataVpFormatSdJwt sdJwtFormat = new OpenIdClientMetadataVpFormatSdJwt(
                List.of("ES256"),
                List.of("ES256")
        );
        OpenidClientMetadataDto metadataWithExisting = OpenidClientMetadataDto.builder()
                .vpFormatsSupported(new OpenIdClientMetadataVpFormatsSupported(sdJwtFormat))
                .encryptedResponseEncValuesSupported(List.of("A256GCM"))
                .additionalProperties(existingAdditionalProps)
                .build();
        Map<String, String> overrides = new HashMap<>();
        overrides.put("client_name", "Overridden Name");
        overrides.put("new_key", "new_value");
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST)
                .build();
        Management management = createManagement(overrides, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(metadataWithExisting);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAdditionalProperties())
                .hasSize(3)
                .containsEntry("existing_key", "existing_value")
                .containsEntry("client_name", "Overridden Name") // Override takes precedence
                .containsEntry("new_key", "new_value");
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_withDirectPostJwtAndOverrides_appliesBothTransformations() {
        // Given
        String jwksJson = "{\"keys\":[{\"kty\":\"EC\"}]}";
        List<String> encryptionValues = List.of("A256GCM");
        Map<String, String> overrides = new HashMap<>();
        overrides.put("client_name", "Test Verifier");
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST_JWT)
                .jwks(jwksJson)
                .encryptedResponseEncValuesSupported(encryptionValues)
                .build();
        Management management = createManagement(overrides, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(baseMetadata);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJwks()).isNotNull();
        assertThat(result.getEncryptedResponseEncValuesSupported()).containsExactlyElementsOf(encryptionValues);
        assertThat(result.getAdditionalProperties())
                .containsEntry("client_name", "Test Verifier");
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_doesNotMutateOriginalMetadata() {
        // Given
        Map<String, Object> originalAdditionalProps = new HashMap<>();
        originalAdditionalProps.put("original_key", "original_value");
        OpenIdClientMetadataVpFormatSdJwt sdJwtFormat = new OpenIdClientMetadataVpFormatSdJwt(
                List.of("ES256"),
                List.of("ES256")
        );
        OpenidClientMetadataDto originalMetadata = OpenidClientMetadataDto.builder()
                .vpFormatsSupported(new OpenIdClientMetadataVpFormatsSupported(sdJwtFormat))
                .encryptedResponseEncValuesSupported(List.of("A256GCM"))
                .additionalProperties(originalAdditionalProps)
                .build();
        Map<String, String> overrides = new HashMap<>();
        overrides.put("new_key", "new_value");
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST)
                .build();
        Management management = createManagement(overrides, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(originalMetadata);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAdditionalProperties())
                .containsEntry("original_key", "original_value")
                .containsEntry("new_key", "new_value");
        // Original metadata should remain unchanged
        assertThat(originalMetadata.getAdditionalProperties())
                .hasSize(1)
                .containsEntry("original_key", "original_value")
                .doesNotContainKey("new_key");
    }

    @Test
    void getOpenidClientMetadataForManagementEntity_withNullEncryptedResponseEncValues_handlesGracefully() {
        // Given
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST_JWT)
                .jwks("{\"keys\":[]}")
                .encryptedResponseEncValuesSupported(null)
                .build();
        Management management = createManagement(null, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(baseMetadata);
        // When
        OpenidClientMetadataDto result = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getJwks()).isNotNull();
        assertThat(result.getEncryptedResponseEncValuesSupported()).isNull();
    }
    @Test
    void getOpenidClientMetadataForManagementEntity_multipleCalls_eachReturnsIndependentCopy() {
        // Given
        ResponseSpecification responseSpec = ResponseSpecification.builder()
                .responseModeType(ResponseModeType.DIRECT_POST)
                .build();
        Management management = createManagement(null, responseSpec);
        when(openIdClientMetadataConfiguration.getVerifierMetadata()).thenReturn(baseMetadata);

        // When - Call the method twice
        OpenidClientMetadataDto firstResult = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);
        OpenidClientMetadataDto secondResult = metadataService.getOpenidClientMetadataForManagementEntity(
                management, responseSpec);

        // Modify the first result
        Map<String, Object> modifiedProps = new HashMap<>();
        modifiedProps.put("modified_in_test", "test_value");
        firstResult.setAdditionalProperties(modifiedProps);

        // Then - Second result should not be affected by modifications to first result
        assertThat(firstResult).isNotSameAs(secondResult);
        assertThat(firstResult.getAdditionalProperties())
                .containsEntry("modified_in_test", "test_value");
        assertThat(secondResult.getAdditionalProperties())
                .doesNotContainKey("modified_in_test");
    }

    /**
     * Helper method to create a Management entity with the given overrides and response specification.
     *
     * @param clientMetadataOverrides optional map of client metadata overrides
     * @param responseSpecification   the response specification
     * @return a Management entity
     */
    private Management createManagement(Map<String, String> clientMetadataOverrides,
                                        ResponseSpecification responseSpecification) {
        ConfigurationOverride configOverride = ConfigurationOverride.builder()
                .clientMetadata(clientMetadataOverrides)
                .build();
        return Management.builder()
                .configurationOverride(configOverride)
                .responseSpecification(responseSpecification)
                .build();
    }
}
