package ch.admin.bj.swiyu.verifier;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.dto.VPApiVersion;
import ch.admin.bj.swiyu.verifier.dto.definition.ConstraintDto;
import ch.admin.bj.swiyu.verifier.dto.definition.InputDescriptorDto;
import ch.admin.bj.swiyu.verifier.dto.definition.PresentationDefinitionDto;
import ch.admin.bj.swiyu.verifier.dto.management.*;
import ch.admin.bj.swiyu.verifier.service.management.fixtures.ApiFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Application.class)
@Nested
@DisplayName("Blackbox Test")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
class BlackboxIT {
    private static final String PUBLIC_KEY = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"oqBwmYd3RAHs-sFe_U7UFTXbkWmPAaqKTHCvsV8tvxU\",\"y\":\"np4PjpDKNfEDk9qwzZPqjAawiZ8sokVOozHR-Kt89T4\"}";
    private static final String ACCEPTED_ISSUER = "did:example:12345";

    private static final String MANAGEMENT_BASE_URL = "/management/api/verifications";
    private static final String OID4VP_API_BASE_URL = "/oid4vp/api/request-object";
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationProperties applicationProperties;
    @MockitoBean
    private DidResolverFacade didResolverFacade;
    @Autowired
    private ManagementRepository managementEntityRepository;

    @ParameterizedTest
    @MethodSource("provideCreateDtosDirectPost")
    void testVerificationFlow_walletSendsValidCredential(CreateVerificationManagementDto createVerificationManagementDto) throws Exception {
        var createDto = objectMapper.writeValueAsString(createVerificationManagementDto);
        var createResponseDto = createVerificationRequest(createDto);

        var nonce = createResponseDto.requestNonce();
        var requestId = createResponseDto.id().toString();

        // Check status, should be pending
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Wallet retrieves Verifier Request
        getVerificationRequestForWallet(requestId, nonce);

        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Wallet checks verifier metadata
        assertDoesNotThrow(() -> mvc.perform(get("/oid4vp/api/openid-client-metadata.json")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value(applicationProperties.getClientId()))
                .andExpect(jsonPath("$.vp_formats.jwt_vp.alg").value(JWSAlgorithm.ES256.getName()))
                .andExpect(jsonPath("$.version").value(applicationProperties.getMetadataVersion()))
                .andReturn());
        // Check status, should still be pending
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Wallet sends valid credential
        var vpToken = createMockCredential(nonce);
        var presentationSubmission = SDJWTCredentialMock.getPresentationSubmissionString(UUID.randomUUID());
        assertDoesNotThrow(() -> mvc.perform(post(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk())
        );
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.SUCCESS));

        // Wallet sends error response, status should not change
        assertDoesNotThrow(() -> mvc.perform(post(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId))
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("error", "vp_formats_not_supported")
                        .formField("error_description", "I really don't want to"))
                .andExpect(status().isGone())
        );
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.SUCCESS));
    }

    @ParameterizedTest
    @MethodSource("provideCreateDtosDirectPost")
    void testVerificationFlow_walletSendsError(CreateVerificationManagementDto createVerificationManagementDto) throws Exception {
        var createDto = objectMapper.writeValueAsString(createVerificationManagementDto);
        var createResponseDto = createVerificationRequest(createDto);

        var nonce = createResponseDto.requestNonce();
        var requestId = createResponseDto.id().toString();

        // Check status, should be pending
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Wallet retrieves Verifier Request
        getVerificationRequestForWallet(requestId, nonce);

        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));
        // Wallet checks verifier metadata
        assertDoesNotThrow(() -> mvc.perform(get("/oid4vp/api/openid-client-metadata.json")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value(applicationProperties.getClientId()))
                .andExpect(jsonPath("$.vp_formats.jwt_vp.alg").value(JWSAlgorithm.ES256.getName()))
                .andExpect(jsonPath("$.version").value(applicationProperties.getMetadataVersion()))
                .andReturn());
        // Check status, should still be pending
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Wallet sends error response
        assertDoesNotThrow(() -> mvc.perform(post(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId))
                        .formField("error", "vp_formats_not_supported")
                        .formField("error_description", "I really don't want to"))
                .andExpect(status().isOk())
        );
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.FAILED));

        // Wallet sends valid credential, should be rejected
        var vpToken = createMockCredential(nonce);
        var presentationSubmission = SDJWTCredentialMock.getPresentationSubmissionString(UUID.randomUUID());
        assertDoesNotThrow(() -> mvc.perform(post(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isGone())
        );
        // Status should not have changed, status should not change
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.FAILED));
    }


    @ParameterizedTest
    @MethodSource("provideCreateDtosDirectPostJwt")
    void testVerificationFlowDirectPostJWT_walletSendsError(CreateVerificationManagementDto createVerificationManagementDto) throws Exception {
        var createDto = objectMapper.writeValueAsString(createVerificationManagementDto);
        var createResponseDto = createVerificationRequest(createDto);

        var nonce = createResponseDto.requestNonce();
        var requestId = createResponseDto.id().toString();

        // Check status, should be pending
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));


        // Wallet checks verifier metadata
        assertDoesNotThrow(() -> mvc.perform(get("/oid4vp/api/openid-client-metadata.json")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value(applicationProperties.getClientId()))
                .andExpect(jsonPath("$.vp_formats.jwt_vp.alg").value(JWSAlgorithm.ES256.getName()))
                .andExpect(jsonPath("$.version").value(applicationProperties.getMetadataVersion()))
                .andReturn());
        // Check status, should still be pending
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.PENDING));

        // Wallet sends error response
        assertDoesNotThrow(() -> mvc.perform(post(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("response", buildJWTResponse(Map.of("error", "vp_formats_not_supported","error_description", "I don't want to"), createResponseDto.id())))
                .andExpect(status().isOk())
        );
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.FAILED));

        // Wallet sends valid credential, should be rejected
        var vpToken = createMockCredential(nonce);
        var presentationSubmission = SDJWTCredentialMock.getPresentationSubmissionString(UUID.randomUUID());
        assertDoesNotThrow(() -> mvc.perform(post(String.format("%s/%s/response-data", OID4VP_API_BASE_URL, requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("response", buildJWTResponse(Map.of("presentation_submission", presentationSubmission,"vp_token", vpToken), createResponseDto.id())))
                .andExpect(status().isGone()));

        // Status should not have changed, status should not change
        assert (hasStatus(createResponseDto.id().toString(), VerificationStatusDto.FAILED));
    }


    private boolean hasStatus(String requestObjectId, VerificationStatusDto status) {
        MvcResult requestObjectResult = assertDoesNotThrow(() -> (mvc.perform(get(String.format("%s/%s", MANAGEMENT_BASE_URL, requestObjectId))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()));
        var createResponse = assertDoesNotThrow(() -> objectMapper.readValue(requestObjectResult.getResponse().getContentAsString(), ManagementResponseDto.class));
        return createResponse.state() == status;
    }

    private void getVerificationRequestForWallet(String requestId, String nonce) throws ParseException, UnsupportedEncodingException, JOSEException {
        MvcResult requestObjectResult = assertDoesNotThrow(() -> (mvc.perform(get(String.format("%s/%s", OID4VP_API_BASE_URL, requestId))
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andReturn()));

        var responseJwt = SignedJWT.parse(requestObjectResult.getResponse().getContentAsString());
        assertThat(responseJwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
        assertThat(responseJwt.getHeader().getKeyID()).isEqualTo(applicationProperties.getSigningKeyVerificationMethod());
        assertThat(responseJwt.verify(new ECDSAVerifier(ECKey.parse(PUBLIC_KEY)))).isTrue();

        // checking claims
        var claims = responseJwt.getJWTClaimsSet();
        assertThat(claims.getStringClaim("response_type")).isEqualTo("vp_token");
        assertThat(claims.getStringClaim("response_mode")).isEqualTo("direct_post");
        assertThat(claims.getStringClaim("nonce")).isEqualTo(nonce);
        assertThat(claims.getStringClaim("response_uri")).isEqualTo(String.format("%s/oid4vp/api/request-object/%s/response-data", applicationProperties.getExternalUrl(), requestId));
    }

    private ManagementResponseDto createVerificationRequest(String body) {
        MvcResult createVerificationResult = assertDoesNotThrow(() -> mvc.perform(post(MANAGEMENT_BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
        );

        return assertDoesNotThrow(() -> objectMapper.readValue(createVerificationResult.getResponse().getContentAsString(), ManagementResponseDto.class));
    }

    private static CreateVerificationManagementDto createDtoAsContentBody(ResponseModeTypeDto responseModeTypeDto) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of(ACCEPTED_ISSUER))
                .jwtSecuredAuthorizationRequest(true)
                .responseMode(responseModeTypeDto)
                .presentationDefinition(PresentationDefinitionDto.builder()
                        .inputDescriptors(List.of(new InputDescriptorDto(
                                UUID.randomUUID().toString(),
                                "input_description_name",
                                "input_description_purpose",
                                ApiFixtures.formatAlgorithmDtoMap(),
                                new ConstraintDto(UUID.randomUUID().toString(), null, null, ApiFixtures.formatAlgorithmDtoMap(), List.of(ApiFixtures.fieldDto(List.of(".first_name"))))
                        )))
                        .id(UUID.randomUUID().toString())
                        .format(ApiFixtures.formatAlgorithmDtoMap())
                        .build()).build();
    }

    private static CreateVerificationManagementDto createDtoAsContentBodyWithDCQL(ResponseModeTypeDto responseModeTypeDto) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of(ACCEPTED_ISSUER))
                .jwtSecuredAuthorizationRequest(true)
                .responseMode(responseModeTypeDto)
                .presentationDefinition(
                        PresentationDefinitionDto.builder()
                        .inputDescriptors(List.of(new InputDescriptorDto(
                                UUID.randomUUID().toString(),
                                "input_description_name",
                                "input_description_purpose",
                                ApiFixtures.formatAlgorithmDtoMap(),
                                // .first_name field in constraints as at least one is required.
                                new ConstraintDto(UUID.randomUUID().toString(), null, null, ApiFixtures.formatAlgorithmDtoMap(), List.of(ApiFixtures.fieldDto(List.of(".first_name"))))
                        )))
                        .id(UUID.randomUUID().toString())
                        .format(ApiFixtures.formatAlgorithmDtoMap())
                        .build()
                ).dcqlQuery(ApiFixtures.getDcqlQueryDto()).build();
    }

    private static Stream<Arguments> provideCreateDtosDirectPost() {
        return Stream.of(
                Arguments.of(createDtoAsContentBody(ResponseModeTypeDto.DIRECT_POST)),
                Arguments.of(createDtoAsContentBodyWithDCQL(ResponseModeTypeDto.DIRECT_POST))
        );
    }

    private static Stream<Arguments> provideCreateDtosDirectPostJwt() {
        return Stream.of(
                Arguments.of(createDtoAsContentBody(ResponseModeTypeDto.DIRECT_POST_JWT)),
                Arguments.of(createDtoAsContentBodyWithDCQL(ResponseModeTypeDto.DIRECT_POST_JWT))
        );
    }

    private String createMockCredential(String nonce) throws NoSuchAlgorithmException, ParseException, JOSEException {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(ACCEPTED_ISSUER, "some_issuer_id#key-1");
        mockDidResolverResponse(emulator);

        var sdJWT = emulator.createSDJWTMock();
        return emulator.addKeyBindingProof(sdJWT, nonce, ACCEPTED_ISSUER);
    }

    private void mockDidResolverResponse(SDJWTCredentialMock sdjwt) {
        try {
            String fragment = "key-1";
            when(didResolverFacade.resolveDid(sdjwt.getIssuerId(), fragment))
                    .thenAnswer(invocation -> DidDocFixtures.issuerDidDocWithJsonWebKey(
                            sdjwt.getIssuerId(),
                            sdjwt.getKidHeaderValue(),
                            KeyFixtures.issuerPublicKeyAsJsonWebKey())
                            .getKey(fragment));
        } catch (DidResolverException | DidSidekicksException e) {
            throw new AssertionError(e);
        }
    }

    private String buildJWTResponse(Map<String,String> fields, UUID requestId) throws ParseException, JOSEException {
        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        var responseSpecification = managementEntity.getResponseSpecification();
        Assertions.assertNotNull(responseSpecification.getJwks());
        ECKey publicKey = JWKSet.parse(responseSpecification.getJwks()).getKeys().getFirst().toECKey();
        var encryptionMethod = EncryptionMethod.parse(responseSpecification.getEncryptedResponseEncValuesSupported().getFirst());

        var claims = new JWTClaimsSet.Builder();
        fields.forEach(claims::claim);


        JWEObject jweObject = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, encryptionMethod)
                        .keyID(publicKey.getKeyID()).build(),
                claims.build().toPayload()
        );
        jweObject.encrypt(new ECDHEncrypter(publicKey));
        return jweObject.serialize();
    }
}
