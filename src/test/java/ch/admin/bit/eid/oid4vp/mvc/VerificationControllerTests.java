package ch.admin.bit.eid.oid4vp.mvc;

import ch.admin.bit.eid.oid4vp.config.ApplicationProperties;
import ch.admin.bit.eid.oid4vp.config.BbsKeyProperties;
import ch.admin.bit.eid.oid4vp.fixtures.DidDocFixtures;
import ch.admin.bit.eid.oid4vp.fixtures.KeyFixtures;
import ch.admin.bit.eid.oid4vp.mock.BBSCredentialMock;
import ch.admin.bit.eid.oid4vp.mock.SDJWTCredentialMock;
import ch.admin.bit.eid.oid4vp.mock.StatusListGenerator;
import ch.admin.bit.eid.oid4vp.model.did.DidResolverAdapter;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.statuslist.StatusListResolverAdapter;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.eid.didresolver.DidResolveException;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static ch.admin.bit.eid.oid4vp.mock.BBSCredentialMock.ExampleJson;
import static ch.admin.bit.eid.oid4vp.mock.SDJWTCredentialMock.getMultiplePresentationSubmissionString;
import static ch.admin.bit.eid.oid4vp.mock.SDJWTCredentialMock.getPresentationSubmissionString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VerificationControllerTests {

    private static final UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    private static final String NONCE_SD_JWT_SQL = "P2vZ8DKAtTuCIU1M7daWLA65Gzoa76tL";
    private static final String publicKey = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"oqBwmYd3RAHs-sFe_U7UFTXbkWmPAaqKTHCvsV8tvxU\",\"y\":\"np4PjpDKNfEDk9qwzZPqjAawiZ8sokVOozHR-Kt89T4\"}";

    @Autowired
    private MockMvc mock;
    @Autowired
    private VerificationManagementRepository verificationManagementRepository;
    @Autowired
    private ApplicationProperties applicationProperties;
    @Autowired
    private BbsKeyProperties bbsKeyProperties;
    @MockBean
    private DidResolverAdapter didResolverAdapter;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private StatusListResolverAdapter mockedStatusListResolverAdapter;

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldGetRequestObject() throws Exception {
        mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andDo(result -> {
                    // checking signature
                    var responseJwt = SignedJWT.parse(result.getResponse().getContentAsString());
                    assert responseJwt.getHeader().getAlgorithm().getName().equals("ES256");
                    assert responseJwt.getHeader().getKeyID().equals(applicationProperties.getSigningKeyVerificationMethod());
                    assert responseJwt.verify(new ECDSAVerifier(ECKey.parse(publicKey)));

                    // checking claims
                    var claims = responseJwt.getJWTClaimsSet();
                    assert claims.getStringClaim("client_id").equals(applicationProperties.getClientId());
                    assert claims.getStringClaim("client_id_scheme").equals(applicationProperties.getClientIdScheme());
                    assert claims.getStringClaim("response_type").equals("vp_token");
                    assert claims.getStringClaim("response_mode").equals("direct_post");
                    assert claims.getStringClaim("nonce") != null;
                    assert claims.getStringClaim("response_uri").equals(String.format("%s/request-object/%s/response-data", applicationProperties.getExternalUrl(), requestId));

                    var presentationDefinition = (LinkedTreeMap) claims.getClaim("presentation_definition");
                    assert presentationDefinition.get("id") != null;
                    assert presentationDefinition.get("name").equals("Presentation Definition Name");
                    assert presentationDefinition.get("purpose").equals("Presentation Definition Purpose");

                    var inputDescriptors = (List<LinkedTreeMap>) presentationDefinition.get("input_descriptors");
                    var inputDescriptor = inputDescriptors.getFirst();

                    assert inputDescriptor.get("id") != null;
                    assert inputDescriptor.get("name").equals("Test Descriptor Name");
                    assert inputDescriptor.get("purpose").equals("Input Descriptor Purpose");

                    var format = (LinkedTreeMap) inputDescriptor.get("format");
                    var ldpVp = (Map<String, List>) format.get("ldp_vp");
                    assert ldpVp.get("proof_type").getFirst().equals("BBS2023");

                    var constraints = (LinkedTreeMap<List, List<LinkedTreeMap<List, List>>>) inputDescriptor.get("constraints");
                    constraints.get("fields").getFirst().get("path").getFirst().equals("$.credentialSubject.hello");

                    var clientMetadata = (LinkedTreeMap) claims.getClaim("client_metadata");
                    assert clientMetadata.get("client_name").equals(applicationProperties.getClientName());
                    assert clientMetadata.get("logo_uri").equals(applicationProperties.getLogoUri());

                    assert !result.getResponse().getContentAsString().contains("null");
                });
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt_no_signing.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldGetUnsignedRequestObject() throws Exception {
        mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value(applicationProperties.getClientId()))
                .andExpect(jsonPath("$.client_id_scheme").value(applicationProperties.getClientIdScheme()))
                .andExpect(jsonPath("$.response_type").value("vp_token"))
                .andExpect(jsonPath("$.response_mode").value("direct_post"))
                .andExpect(jsonPath("$.nonce").isNotEmpty())
                .andExpect(jsonPath("$.response_uri").value(String.format("%s/request-object/%s/response-data", applicationProperties.getExternalUrl(), requestId)))
                .andExpect(jsonPath("$.presentation_definition.id").isNotEmpty())
                .andExpect(jsonPath("$.presentation_definition.name").value("Presentation Definition Name"))
                .andExpect(jsonPath("$.presentation_definition.purpose").value("Presentation Definition Purpose"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].id").isNotEmpty())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].name").value("Test Descriptor Name"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].purpose").value("Input Descriptor Purpose"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].format.ldp_vp").isNotEmpty())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].format.ldp_vp.proof_type").value("BBS2023"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path[0]").value("$.credentialSubject.hello"))
                .andExpect(jsonPath("client_metadata.client_name").value(applicationProperties.getClientName()))
                .andExpect(jsonPath("client_metadata.logo_uri").value(applicationProperties.getLogoUri()))

                .andExpect(content().string(not(containsString("null")))).andReturn();
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldAcceptRefusal() throws Exception {
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .formField("error", "I_dont_want_to")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isOk());
        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        assert managementEntity.getState() == VerificationStatusEnum.FAILED;
    }

    /**
     * The Verification of the Credential should not succeed because there is no VDR
     */
    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingBBSCredential() throws Exception {
        var emulator = new BBSCredentialMock(bbsKeyProperties);

        var credential = emulator.createVC(
                List.of("/credentialSubject/id", "/type", "/issuer"),
                emulator.addHolderBinding(ExampleJson));

        // Fetch the Request Object
        var response = mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andReturn();

        JWTClaimsSet responseContent = validateSignatureAndGetJWTClaimsSet(response.getResponse());
        String nonce = responseContent.getStringClaim("nonce");
        String vpToken = emulator.createVerifiablePresentationUrlEncodedHolderBinding(credential, List.of("/credentialSubject/hello"), nonce);
        PresentationSubmission presentationSubmission = emulator.getCredentialSubmission();

        String presentationSubmissionString = objectMapper.writeValueAsString(presentationSubmission);

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmissionString)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        assert managementEntity.getWalletResponse().getCredentialSubjectData().contains("world");

        // Test Resending after Success

        response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .formField("presentation_submission", presentationSubmissionString)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        //Data should be unchanged
        managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        assert managementEntity.getWalletResponse().getCredentialSubjectData().contains("world");

        // Error that verification is closed should be returned to wallet
        var responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED.toString());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingCredentialNoHolderBinding() throws Exception {

        var emulator = new BBSCredentialMock(bbsKeyProperties);

        var credential = emulator.createVC(
                List.of("/type", "/issuer"),
                ExampleJson);

        // Fetch the Request Object
        var response = mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andReturn();

        JWTClaimsSet responseContent = validateSignatureAndGetJWTClaimsSet(response.getResponse());
        String nonce = responseContent.getStringClaim("nonce");
        String vpToken = emulator.createVerifiablePresentationUrlEncoded(credential, List.of("/credentialSubject/hello"), nonce);
        PresentationSubmission presentationSubmission = emulator.getCredentialSubmission();

        String presentationSubmissionString = objectMapper.writeValueAsString(presentationSubmission);

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmissionString)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        assert managementEntity.getWalletResponse().getCredentialSubjectData().contains("world");

        // Test Resending after Success

        response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .formField("presentation_submission", presentationSubmissionString)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        //Data should be unchanged
        managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        assert managementEntity.getWalletResponse().getCredentialSubjectData().contains("world");

        // Error that verification is closed should be returned to wallet
        var responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED.toString());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldFailOnInvalidPresentationSubmissionWithAdditionallyEncodedString() throws Exception {

        var emulator = new BBSCredentialMock(bbsKeyProperties);

        var credential = emulator.createVC(
                List.of("/credentialSubject/id", "/type", "/issuer"),
                emulator.addHolderBinding(ExampleJson));
        var response = mock.perform(get(String.format("/request-object/%s", requestId))).andReturn();

        JWTClaimsSet responseContent = validateSignatureAndGetJWTClaimsSet(response.getResponse());
        String nonce = responseContent.getStringClaim("nonce");
        String vpToken = emulator.createVerifiablePresentationUrlEncodedHolderBinding(credential, List.of("/credentialSubject/hello"), nonce);
        PresentationSubmission presentationSubmission = emulator.getCredentialSubmission();

        String presentationSubmissionString = objectMapper.writeValueAsString(presentationSubmission);

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", Base64.getUrlEncoder().encodeToString(presentationSubmissionString.getBytes(StandardCharsets.UTF_8)))
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid presentation submission"));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldFailOnInvalidPresentationSubmissionWithInvalidJson() throws Exception {

        var emulator = new BBSCredentialMock(bbsKeyProperties);

        var credential = emulator.createVC(
                List.of("/credentialSubject/id", "/type", "/issuer"),
                emulator.addHolderBinding(ExampleJson));
        var response = mock.perform(get(String.format("/request-object/%s", requestId))).andReturn();

        JWTClaimsSet responseContent = validateSignatureAndGetJWTClaimsSet(response.getResponse());
        String nonce = responseContent.getStringClaim("nonce");
        String vpToken = emulator.createVerifiablePresentationUrlEncodedHolderBinding(credential, List.of("/credentialSubject/hello"), nonce);
        String presentationSubmission = "{\"id\":\"test_ldp_vc_presentation_definition\",\"definition_id\":\"ldp_vc\",\"descriptor_map\":[{\"id\":\"test_descriptor\",\"format\":\"ldp_vp\",\"path\":\"$.credentialSubject\",\"path_nested\":null";

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid presentation submission"));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldFailVerifyingCredentialWrongNonce() throws Exception {

        var emulator = new BBSCredentialMock(bbsKeyProperties);

        var jsonData = "{\"issuer\": \"did:example:12345\", \"type\": [\"VerifiableCredential\", \"ExampleCredential\"], \"credentialSubject\": {\"hello\": \"world\"}}";
        var credential = emulator.createVC(
                List.of("/credentialSubject/id", "/type", "/issuer"),
                emulator.addHolderBinding(jsonData));

        // Fetch the Request Object
        mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andReturn();

        String nonce = "wrong_nonce";
        String vpToken = emulator.createVerifiablePresentationUrlEncodedHolderBinding(credential, List.of("/credentialSubject/hello"), nonce);
        PresentationSubmission presentationSubmission = emulator.getCredentialSubmission();

        String presentationSubmissionString = objectMapper.writeValueAsString(presentationSubmission);

        var response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .formField("vp_token", vpToken)
                        .formField("presentation_submission", presentationSubmissionString))
                .andExpect(status().isBadRequest())
                .andReturn();

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        // Should be failed state
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        // Data should not be saved
        Assert.doesNotContain(managementEntity.getWalletResponse().getCredentialSubjectData(), "world",
                String.format("Expecting no data to be saved on failure, found %s",
                        managementEntity.getWalletResponse().getCredentialSubjectData()));
        // Error code should be set in management entity
        Assert.state(managementEntity.getWalletResponse().getErrorCode() == ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                String.format("Expecting error code to be %s but was %s",
                        ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH,
                        managementEntity.getWalletResponse().getErrorCode()));
        // Error & error code should be returned to wallet
        var responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(ResponseErrorCodeEnum.HOLDER_BINDING_MISMATCH.toString());
        assert responseBody.contains(VerificationErrorEnum.INVALID_REQUEST.toString());

        // Resending after failure
        response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .param("vp_token", vpToken)
                        .formField("presentation_submission", presentationSubmissionString))
                .andExpect(status().isBadRequest())
                .andReturn();

        // Error that verification is closed should be returned to wallet
        responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED.toString());

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldRespond404onGetRequestObject() throws Exception {
        UUID notExistingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        mock.perform(get(String.format("/request-object/%s", notExistingRequestId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldRespond404onPostResponseData() throws Exception {
        UUID notExistingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        mock.perform(post(String.format("/request-object/%s/response-data", notExistingRequestId))
                        .formField("error", "trying_to_get_404")
                        .formField("error_description", "mimi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND.toString()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingSDJWTCredentialFullVC_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2"})
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingSDJWTCredentialWithSD_thenSuccess(String input) throws Exception {
        Integer statusListIndex = "".equals(input) ? null : Integer.parseInt(input);
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT), any())).thenAnswer(invocation -> {
            var statusListGenerator = new StatusListGenerator(emulator.getKey(), emulator.getIssuerId(), emulator.getKidHeaderValue());
            return statusListGenerator.createTokenStatusListTokenVerifiableCredential(StatusListGenerator.SPEC_STATUS_LIST);
        });

        var sdJWT = emulator.createSDJWTMock(statusListIndex);
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk()).andReturn();

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @ParameterizedTest
    @CsvSource(value = {"0:credential_revoked", "1:credential_suspended", "3:credential_revoked"}, delimiter = ':')
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingSDJWTCredentialWithSD_thenFail(String input, String errorCodeName) throws Exception {
        Integer index = "".equals(input) ? null : Integer.parseInt(input);
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT), any())).thenAnswer(invocation -> {
            var statusListGenerator = new StatusListGenerator(emulator.getKey(), emulator.getIssuerId(), emulator.getKidHeaderValue());
            return statusListGenerator.createTokenStatusListTokenVerifiableCredential(StatusListGenerator.SPEC_STATUS_LIST);
        });

        var sdJWT = emulator.createSDJWTMock(index);
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response, so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest()).andReturn();

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        var errorCode = managementEntity.getWalletResponse().getErrorCode().getDisplayName().equals(errorCodeName);
        Assert.state(errorCode, String.format("Expected state to be %s, but got %s", errorCodeName, errorCode));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void twoTimesSameDisclosures_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~" + StringUtils.join(sd, "~") + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");

        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("errorDescription").value("Request contains non-distinct disclosures"));

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt_different_algs.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void wrongAlgorithm_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));

        var responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(VerificationErrorEnum.INVALID_REQUEST.toString());
        assert responseBody.contains("Invalid algorithm");
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt_different_kb_algs.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void wrongKeyBindingAlgorithm_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        var response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));

        var responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(VerificationErrorEnum.INVALID_REQUEST.toString());
        assert responseBody.contains("holder_binding_mismatch");
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void notYetValid_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(Instant.now().plus(7, ChronoUnit.DAYS).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("errorDescription").value("Could not verify JWT credential is not yet valid"));

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void sdJWTExpired_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");

        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("errorDescription").value("Could not verify JWT credential is expired"));

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void sdJWTAdditionalDisclosure_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var additionalDisclosure = new Disclosure("additional", "definetly_wrong");
        var newCred = sdJWT + additionalDisclosure + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");

        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("errorDescription").value("Could not verify JWT problem with disclosures and _sd field"));

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingNestedSDJWTCredentialSD_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        vpToken = emulator.createMultipleVPTokenMock(vpToken);
        String presentationSubmission = getMultiplePresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldVerifyingSDJWTCredentialSDWithDifferentPrivKey_thenException() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("errorDescription").value("Signature mismatch"));
    }

    private void mockDidResolverResponse(SDJWTCredentialMock sdjwt) {
        try {
            when(didResolverAdapter.resolveDid(sdjwt.getIssuerId())).thenAnswer(invocation -> DidDocFixtures.issuerDidDocWithMultikey(
                    sdjwt.getIssuerId(),
                    sdjwt.getKidHeaderValue(),
                    KeyFixtures.issuerPublicKeyAsMultibaseKey()));
        } catch (DidResolveException e) {
            throw new AssertionError(e);
        }

    }

    private JWTClaimsSet validateSignatureAndGetJWTClaimsSet(MockHttpServletResponse response) throws Exception {
        String rawResponse = response.getContentAsString();
        SignedJWT responseJwt = SignedJWT.parse(rawResponse);
        assert responseJwt.verify(new ECDSAVerifier(ECKey.parse(publicKey)));
        return responseJwt.getJWTClaimsSet();
    }
}
