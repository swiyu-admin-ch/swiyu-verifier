package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.CredentialEvaluation;
import ch.admin.bj.swiyu.verifier.domain.IssuerTrustMarker;
import ch.admin.bj.swiyu.verifier.domain.StatusVerificationResult;
import ch.admin.bj.swiyu.verifier.domain.TrustMethod;
import ch.admin.bj.swiyu.verifier.domain.VerificationResultData;
import ch.admin.bj.swiyu.verifier.domain.management.*;
import ch.admin.bj.swiyu.verifier.dto.VerificationClientErrorDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationErrorResponseCodeDto;
import ch.admin.bj.swiyu.verifier.dto.management.ConfigurationOverrideDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.dto.management.TrustAnchorDto;
import ch.admin.bj.swiyu.verifier.dto.management.VerificationStatusDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.service.management.ManagementMapper.toManagementResponseDto;
import static ch.admin.bj.swiyu.verifier.service.management.fixtures.ManagementFixtures.management;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagementMapperTest {

    private static final String EXTERNAL_URL = "https://example.com/oid4vp";
    private static final String CLIENT_ID = "client_id";
    private static final String DEEPLINK_SCHEMA = "openid4vp";
    private static final String CLIENT_ID_PREFIX = "decentralized_identifier";
    private static final ObjectMapper mapper = new ObjectMapper();

    private ApplicationProperties applicationProperties;

    @BeforeEach
    void setUp() {
        applicationProperties = mock(ApplicationProperties.class);
        when(applicationProperties.getExternalUrl()).thenReturn(EXTERNAL_URL);
        when(applicationProperties.getClientId()).thenReturn(CLIENT_ID);
        when(applicationProperties.getDeeplinkSchema()).thenReturn(DEEPLINK_SCHEMA);
        when(applicationProperties.getClientIdPrefix()).thenReturn(CLIENT_ID_PREFIX);
    }

    @Test
    void toManagementResponseDto_withPendingManagement_returnsDtoWithVerificationUrls() {
        var management = management();
        var expectedVerificationUrl = "%s/oid4vp/api/request-object/%s".formatted(EXTERNAL_URL, management.getId());
        var expectedDeeplink = expectedVerificationDeeplink(
                DEEPLINK_SCHEMA,
                CLIENT_ID_PREFIX + ":" + CLIENT_ID,
                expectedVerificationUrl
        );

        var dto = toManagementResponseDto(management, applicationProperties);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(management.getId());
        assertThat(dto.requestNonce()).isNotBlank();
        assertThat(dto.state()).isEqualTo(VerificationStatusDto.PENDING);
        assertThat(dto.walletResponse()).isNull();
        assertThat(dto.verificationUrl()).isEqualTo(expectedVerificationUrl);
        assertThat(dto.verificationDeeplink()).isEqualTo(expectedDeeplink);
        assertThat(dto.verificationDeeplink()).startsWith("%s://?client_id".formatted(DEEPLINK_SCHEMA));
    }

    @Test
    void toManagementResponseDto_withInProgressManagement_returnsPendingState() {
        var management = management();
        management.claimForProcessing();

        var dto = toManagementResponseDto(management, applicationProperties);

        assertThat(dto.state()).isEqualTo(VerificationStatusDto.PENDING);
    }

    /**
     * Create a complete verification result and tests mapping to the DTO and serializing it
     */
    @Test
    void toManagementResponseDto_withSuccessfulVerification_returnsVerificationArtefacts() {
        String dcqlId = "requested_data";
        String vpTokenStandin = "test_vp_token";
        var management = managementWithOverride();
        management.claimForProcessing();
        var credentialEvaluation = CredentialEvaluation.builder()
            .credentialStatus(StatusVerificationResult.builder()
                .valid(true)
                .status(0)
                .build())
            .trustMarkers(IssuerTrustMarker.builder()
                .isTrusted(true)
                .trustMethod(TrustMethod.TRUST_PROTOCOL_2_0)
                .identityTrustMarker(true)
                .compliantActorTrustMarker(true)
                .governedUseCaseTrustMarker(false)
                .governedUseCaseAuthorizationTrustMarker(false)
                .build())
            .build();
        management.verificationDone(VerificationResultData.builder()
            .verifiedResponsesJsonString("{\"given_name\":\"Ada\",\"age\":42}")
            .evaluations(Map.of(dcqlId, List.of(credentialEvaluation)))
            .vpTokens(Map.of(dcqlId, List.of(vpTokenStandin)))
            .build());

        var dto = toManagementResponseDto(management, applicationProperties);

        var expectedVerificationUrl = "https://override.example.com/verifier/oid4vp/api/request-object/%s"
                .formatted(management.getId());
        var expectedDeeplink = expectedVerificationDeeplink(
                DEEPLINK_SCHEMA,
                CLIENT_ID_PREFIX + ":did:example:override",
                expectedVerificationUrl
        );

        assertThat(dto.state()).isEqualTo(VerificationStatusDto.SUCCESS);
        assertThat(dto.walletResponse()).isNotNull();
        assertThat(dto.walletResponse().errorCode()).isNull();
        assertThat(dto.walletResponse().errorDescription()).isNull();
        assertThat(dto.walletResponse().credentialSubjectData())
                .containsEntry("given_name", "Ada")
                .containsEntry("age", 42);
        assertThat(dto.verificationUrl()).isEqualTo(expectedVerificationUrl);
        assertThat(dto.verificationDeeplink()).isEqualTo(expectedDeeplink);
        assertThat(dto.credentialEvaluation()).hasSize(1);
        var evaluationDtos = dto.credentialEvaluation().get(dcqlId);
        assertThat(evaluationDtos).hasSize(1);
        var evaluationDto = evaluationDtos.getFirst();
        var statusDto = evaluationDto.credentialStatus();
        assertThat(statusDto.valid()).isTrue();
        assertThat(statusDto.status()).isEqualTo(0);
        var trustDto = evaluationDto.trustMarkers();
        assertThat(trustDto.isTrusted()).isTrue();
        assertThat(trustDto.identityTrustMarker()).isTrue();
        assertThat(trustDto.compliantActorTrustMarker()).isTrue();
        assertThat(trustDto.governedUseCaseTrustMarker()).isFalse();
        assertThat(trustDto.governedUseCaseAuthorizationTrustMarker()).isFalse();

        assertThat(dto.walletResponse().vpToken().get(dcqlId)).hasSize(1).contains(vpTokenStandin);
        var json = assertDoesNotThrow(() -> mapper.writeValueAsString(dto));
        assertThat(json).as("Interface defined fields must exist in serialized string").contains(
            dcqlId,
            "credential_status",
            "trust_markers",
            "trust_method",
            "TRUST_PROTOCOL_2_0",
            "is_trusted",
            "viTM",
            "caTM",
            "gucTM",
            "gucaTM", 
            "vp_token",
            vpTokenStandin,
            "credential_subject_data");
    }

    @Test
    void toManagementResponseDto_withFailedVerification_returnsMappedErrorResponse() {
        var management = management();
        management.claimForProcessing();
        management.verificationFailed(VerificationErrorResponseCode.ACCESS_DENIED, "wallet rejected");

        var dto = toManagementResponseDto(management, applicationProperties);

        assertThat(dto.state()).isEqualTo(VerificationStatusDto.FAILED);
        assertThat(dto.walletResponse()).isNotNull();
        assertThat(dto.walletResponse().errorCode()).isEqualTo(VerificationErrorResponseCodeDto.ACCESS_DENIED);
        assertThat(dto.walletResponse().errorDescription()).isEqualTo("wallet rejected");
        assertThat(dto.walletResponse().credentialSubjectData()).isNull();
    }

    @Test
    void toManagementResponseDto_withBlankClientIdPrefix_omitsPrefixInDeeplink() {
        when(applicationProperties.getClientIdPrefix()).thenReturn("");
        var management = management();
        var expectedVerificationUrl = "%s/oid4vp/api/request-object/%s".formatted(EXTERNAL_URL, management.getId());
        var expectedDeeplink = expectedVerificationDeeplink(DEEPLINK_SCHEMA, CLIENT_ID, expectedVerificationUrl);

        var dto = toManagementResponseDto(management, applicationProperties);

        assertThat(dto.verificationDeeplink()).isEqualTo(expectedDeeplink);
    }

    @Test
    void toManagementResponseDto_withMalformedCredentialSubjectData_throwsIllegalArgumentException() {
        var management = Management.builder()
                .id(UUID.randomUUID())
                .expirationInSeconds(900)
                .jwtSecuredAuthorizationRequest(true)
                .acceptedIssuerDids(List.of("did:example:123"))
                .walletResponse(ResponseData.builder().credentialSubjectData("not-json").build())
                .build()
                .resetExpiresAt();

        assertThatThrownBy(() -> toManagementResponseDto(management, applicationProperties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid string cannot be converted to map");
    }

    @Test
    void toTrustAnchor_withDto_returnsDomainObject() {
        var dto = new TrustAnchorDto("did:example:123", "https://registry.example");

        var result = ManagementMapper.toTrustAnchor(dto);

        assertThat(result).isEqualTo(new TrustAnchor(dto.did(), dto.trustRegistryUri()));
    }

    @Test
    void toTrustAnchors_withNullInput_returnsEmptyList() {
        assertThat(ManagementMapper.toTrustAnchors(null)).isEmpty();
    }

    @Test
    void toTrustAnchors_withDtos_returnsMappedList() {
        var trustAnchors = List.of(
                new TrustAnchorDto("did:example:123", "https://registry-one.example"),
                new TrustAnchorDto("did:example:456", "https://registry-two.example")
        );

        var result = ManagementMapper.toTrustAnchors(trustAnchors);

        assertThat(result).containsExactly(
                new TrustAnchor("did:example:123", "https://registry-one.example"),
                new TrustAnchor("did:example:456", "https://registry-two.example")
        );
    }

    @Test
    void toVerificationErrorResponseCode_withNullInput_returnsNull() {
        assertThat(ManagementMapper.toVerificationErrorResponseCode(null)).isNull();
    }

    @ParameterizedTest
    @EnumSource(VerificationClientErrorDto.class)
    void toVerificationErrorResponseCode_withClientError_returnsDomainErrorCode(
            VerificationClientErrorDto clientErrorDto) {
        assertThat(ManagementMapper.toVerificationErrorResponseCode(clientErrorDto))
                .isEqualTo(VerificationErrorResponseCode.valueOf(clientErrorDto.name()));
    }

    @Test
    void toSigningOverride_withNullInput_returnsNull() {
        assertThat(ManagementMapper.toSigningOverride(null)).isNull();
    }

    @Test
    void toSigningOverride_withDto_returnsDomainOverride() {
        var overrideDto = new ConfigurationOverrideDto(
                "https://override.example.com",
                "did:example:override",
                "did:example:override#key-1",
                "key-id-1",
                "1234",
                Map.of("client_name", "Verifier")
        );

        var result = ManagementMapper.toSigningOverride(overrideDto);

        assertThat(result).isEqualTo(new ConfigurationOverride(
                overrideDto.externalUrl(),
                overrideDto.verifierDid(),
                overrideDto.verificationMethod(),
                overrideDto.keyId(),
                overrideDto.keyPin(),
                overrideDto.clientMetadata()
        ));
    }

    @ParameterizedTest
    @EnumSource(ResponseModeTypeDto.class)
    void toResponseMode_withDto_returnsDomainEnum(ResponseModeTypeDto responseModeTypeDto) {
        assertThat(ManagementMapper.toResponseMode(responseModeTypeDto))
                .isEqualTo(ResponseModeType.valueOf(responseModeTypeDto.name()));
    }

    @ParameterizedTest
    @EnumSource(ResponseModeType.class)
    void toResponseModeDto_withDomainEnum_returnsDto(ResponseModeType responseModeType) {
        assertThat(ManagementMapper.toResponseModeDto(responseModeType))
                .isEqualTo(ResponseModeTypeDto.valueOf(responseModeType.name()));
    }

    @Test
    void toJWKSetDto_withValidJson_returnsParsedKeySet() {
        var jwks = """
                {
                  "keys": [
                    {
                      "kty": "EC",
                      "kid": "kid-1",
                      "use": "enc",
                      "alg": "ECDH-ES",
                      "crv": "P-256",
                      "x": "x-value",
                      "y": "y-value"
                    }
                  ]
                }
                """;

        var result = ManagementMapper.toJWKSetDto(jwks);

        assertThat(result.keys()).hasSize(1);
        assertThat(result.keys().getFirst().kty()).isEqualTo("EC");
        assertThat(result.keys().getFirst().kid()).isEqualTo("kid-1");
    }

    @Test
    void toJWKSetDto_withInvalidJson_throwsIllegalStateException() {
        assertThatThrownBy(() -> ManagementMapper.toJWKSetDto("not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Malformed Json Web Key Set saved");
    }

    @Test
    void uriToVerificationPresentation_withUri_returnsResponseDto() {
        var uri = URI.create("https://wallet.example/callback?response_code=123");

        var result = ManagementMapper.uriToVerificationPresentation(uri);

        assertThat(result.redirectURI()).isEqualTo(uri);
    }

    private Management managementWithOverride() {
        return Management.builder()
                .id(UUID.randomUUID())
                .expirationInSeconds(900)
                .jwtSecuredAuthorizationRequest(true)
                .acceptedIssuerDids(List.of("did:example:123"))
                .configurationOverride(ConfigurationOverride.builder()
                        .externalUrl("https://override.example.com/verifier")
                        .verifierDid("did:example:override")
                        .build())
                .build()
                .resetExpiresAt();
    }

    private String expectedVerificationDeeplink(String deeplinkSchema, String clientId, String requestUri) {
        var urlEncodedClientId = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        var urlEncodedRequestUri = URLEncoder.encode(requestUri, StandardCharsets.UTF_8);
        return "%s://?client_id=%s&request_uri=%s".formatted(deeplinkSchema, urlEncodedClientId, urlEncodedRequestUri);
    }
}
