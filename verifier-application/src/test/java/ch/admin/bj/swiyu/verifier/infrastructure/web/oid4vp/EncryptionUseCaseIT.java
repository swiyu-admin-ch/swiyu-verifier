package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.dto.management.VerificationStatusDto;
import ch.admin.bj.swiyu.verifier.service.management.fixtures.ApiFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Encryption Test")
class EncryptionUseCaseIT extends BaseVerificationControllerTest {
    private static final String ACCEPTED_ISSUER = "did:webvh:scid:example.com:api:v1:some-issuer-id";
    private static final String VERIFIER_DID = "did:example:12345";

    private static final String MANAGEMENT_BASE_URL = "/management/api/verifications";
    private static final String OID4VP_API_BASE_URL = "/oid4vp/api/request-object";

    @ParameterizedTest
    @MethodSource("provideCreateDtos")
    void testVerificationFlow_withAndWithoutRequestedEncryption_thenSuccess(CreateVerificationManagementDto createVerificationManagementDto) throws Exception {
        var createDto = objectMapper.writeValueAsString(createVerificationManagementDto);
        var createResponseDto = createVerificationRequest(createDto);
        var requestId = createResponseDto.id().toString();

        // Wallet retrieves Verifier Request
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", requestId));
        var nonce = requestObject.getJWTClaimsSet().getClaim("nonce").toString();

        // Check status, should be pending
        assertTrue(hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Check status, should still be pending
        assertTrue(hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Wallet sends valid credential
        assertDoesNotThrow(() -> sendVerificationResponse(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId), createMockCredential(nonce), requestObject)
                .andExpect(status().isOk()));

        // Status should not have changed, status should not change
        assertTrue(hasStatus(createResponseDto.id().toString(), VerificationStatusDto.SUCCESS));
    }

    @ParameterizedTest
    @MethodSource("provideCreateDtos")
    void testVerificationFlow_withAndWithoutRequestedEncryption_thenWalletSendsReject(CreateVerificationManagementDto createVerificationManagementDto) throws Exception {
        var createDto = objectMapper.writeValueAsString(createVerificationManagementDto);
        var createResponseDto = createVerificationRequest(createDto);
        var requestId = createResponseDto.id().toString();

        // Wallet retrieves Verifier Request
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", requestId));
        var nonce = requestObject.getJWTClaimsSet().getClaim("nonce").toString();

        // Check status, should be pending
        assertTrue(hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Check status, should still be pending
        assertTrue(hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Wallet sends error response
        assertDoesNotThrow(() -> sendVerificationRejection(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId), "vp_formats_not_supported", "I don't want to", requestObject)
                .andExpect(status().isOk()));

        assertTrue(hasStatus(createResponseDto.id().toString(), VerificationStatusDto.FAILED));

        // Wallet sends valid credential, should be rejected
        var vpToken = createMockCredential(nonce);

        assertDoesNotThrow(() -> sendVerificationResponse(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId), vpToken, requestObject)
                .andExpect(status().isGone()));

        // Status should not have changed, status should not change
        assertTrue(hasStatus(createResponseDto.id().toString(), VerificationStatusDto.FAILED));
    }

    @Test
    void encryptedResponse_whenCompressedCipherTextExceedsConfiguredLimit_thenBadRequestAndRemainsPending()
            throws Exception {
        // GIVEN
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED).orElseThrow();
        var responseSpecification = managementEntity.getResponseSpecification();
        ECKey publicKey = JWKSet.parse(responseSpecification.getJwks()).getKeys().getFirst().toECKey();
        String payload = new JWTClaimsSet.Builder()
                .claim("vp_token", Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of("payload".repeat(1_000))))
                .claim("state", REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED.toString())
                .build()
                .toString();
        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                        .compressionAlgorithm(CompressionAlgorithm.DEF)
                        .keyID(publicKey.getKeyID())
                        .build(),
                new Payload(payload));
        jweObject.encrypt(new ECDHEncrypter(publicKey));
        int compressedCipherTextLength = jweObject.getCipherText().toString().length();
        doReturn(compressedCipherTextLength - 1)
                .when(applicationProperties).getMaxCompressedCipherTextLength();

        // WHEN / THEN
        mockMvc.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("response", jweObject.serialize()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_description").value("Response cannot be decrypted."));

        assertThat(managementEntityRepository.findById(REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED).orElseThrow().getState())
                .isEqualTo(PENDING);
    }

    private boolean hasStatus(String requestObjectId, VerificationStatusDto status) {
        MvcResult requestObjectResult = assertDoesNotThrow(() -> (mockMvc.perform(get(String.format("%s/%s", MANAGEMENT_BASE_URL, requestObjectId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()));
        var createResponse = assertDoesNotThrow(() -> objectMapper.readValue(requestObjectResult.getResponse().getContentAsString(), ManagementResponseDto.class));
        return createResponse.state() == status;
    }

    private ManagementResponseDto createVerificationRequest(String body) {
        MvcResult createVerificationResult = assertDoesNotThrow(() -> mockMvc.perform(post(MANAGEMENT_BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
        );

        return assertDoesNotThrow(() -> objectMapper.readValue(createVerificationResult.getResponse().getContentAsString(), ManagementResponseDto.class));
    }

    private static CreateVerificationManagementDto createNestedDtoAsContentBodyWithDCQL(ResponseModeTypeDto responseModeTypeDto) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of(ACCEPTED_ISSUER))
                .jwtSecuredAuthorizationRequest(true)
                .responseMode(responseModeTypeDto)
                .dcqlQuery(ApiFixtures.getDcqlQueryForNestedAddressDto()).build();
    }

    private static Stream<Arguments> provideCreateDtos() {
        return Stream.of(
                Arguments.of(createNestedDtoAsContentBodyWithDCQL(ResponseModeTypeDto.DIRECT_POST)),
                Arguments.of(createNestedDtoAsContentBodyWithDCQL(ResponseModeTypeDto.DIRECT_POST_JWT))
        );
    }

    /**
     * Create a recursive credential including an always revealed iss claim
     */
    private String createMockCredential(String nonce) throws NoSuchAlgorithmException, ParseException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(ACCEPTED_ISSUER, ACCEPTED_ISSUER + "#key-1");
        mockDidResolverResponse(emulator);

        var sdJWT = emulator.createSDJWTMockWithRecursiveListArray();
        return emulator.addKeyBindingProof(sdJWT, nonce, "decentralized_identifier:" + VERIFIER_DID);
    }
}