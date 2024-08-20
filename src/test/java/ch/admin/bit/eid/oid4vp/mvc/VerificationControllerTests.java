package ch.admin.bit.eid.oid4vp.mvc;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.mock.BBSCredentialMock;
import ch.admin.bit.eid.oid4vp.mock.SDJWTCredentialMock;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static ch.admin.bit.eid.oid4vp.mock.BBSCredentialMock.ExampleJson;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@ActiveProfiles("test")
@AutoConfigureMockMvc
// @ExtendWith(SpringExtension.class)
class VerificationControllerTests {

    @Autowired
    private MockMvc mock;

    @Autowired
    private VerificationManagementRepository verificationManagementRepository;

    @Autowired
    private ApplicationConfiguration applicationConfiguration;

    @Autowired
    private BBSKeyConfiguration bbsKeyConfiguration;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final static UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldGetRequestObject() throws Exception {
        mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value(applicationConfiguration.getClientId()))
                .andExpect(jsonPath("$.client_id_scheme").value(applicationConfiguration.getClientIdScheme()))
                .andExpect(jsonPath("$.response_type").value("vp_token"))
                .andExpect(jsonPath("$.response_mode").value("direct_post"))
                .andExpect(jsonPath("$.nonce").isNotEmpty())
                .andExpect(jsonPath("$.response_uri").value(String.format("%s/request-object/%s/response-data", applicationConfiguration.getExternalUrl(), requestId)))
                .andExpect(jsonPath("$.presentation_definition[0].id").value("test_descriptor_id"))
                .andExpect(jsonPath("$.presentation_definition[0].name").value("Test Descriptor Name"))
                .andExpect(jsonPath("client_metadata.client_name").value(applicationConfiguration.getClientName()))
                .andExpect(jsonPath("client_metadata.logo_uri").value(applicationConfiguration.getLogoUri()))

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
    void shouldSucceedVerifyingCredential() throws Exception {

        var emulator = new BBSCredentialMock(bbsKeyConfiguration);

        var credential = emulator.createVC(List.of("/type", "/issuer"), ExampleJson);

        // Fetch the Request Object
        var response = mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andReturn();

        // Get the Nonce
        JsonObject responseContent = JsonParser.parseString(response.getResponse().getContentAsString()).getAsJsonObject();
        String nonce = responseContent.get("nonce").getAsString();
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

        var emulator = new BBSCredentialMock(bbsKeyConfiguration);

        var credential = emulator.createVC(List.of("/type", "/issuer"), ExampleJson);
        var response = mock.perform(get(String.format("/request-object/%s", requestId))).andReturn();

        // Get the Nonce
        JsonObject responseContent = JsonParser.parseString(response.getResponse().getContentAsString()).getAsJsonObject();
        String nonce = responseContent.get("nonce").getAsString();
        String vpToken = emulator.createVerifiablePresentationUrlEncoded(credential, List.of("/credentialSubject/hello"), nonce);
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

        var emulator = new BBSCredentialMock(bbsKeyConfiguration);

        var credential = emulator.createVC(List.of("/type", "/issuer"), ExampleJson);
        var response = mock.perform(get(String.format("/request-object/%s", requestId))).andReturn();

        // Get the Nonce
        JsonObject responseContent = JsonParser.parseString(response.getResponse().getContentAsString()).getAsJsonObject();
        String nonce = responseContent.get("nonce").getAsString();
        String vpToken = emulator.createVerifiablePresentationUrlEncoded(credential, List.of("/credentialSubject/hello"), nonce);
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

        var emulator = new BBSCredentialMock(bbsKeyConfiguration);

        var credential = emulator.createVC(
                List.of("/type", "/issuer"),
                "{\"issuer\": \"did:example:12345\", \"type\": [\"VerifiableCredential\", \"ExampleCredential\"], \"credentialSubject\": {\"hello\": \"world\"}}");

        // Fetch the Request Object
        mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andReturn();

        String nonce = "wrong_nonce";
        String vpToken = emulator.createVerifiablePresentationUrlEncoded(credential, List.of("/credentialSubject/hello"), nonce);
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
        Assert.state(managementEntity.getWalletResponse().getErrorCode() == ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                String.format("Expecting error code to be %s but was %s",
                        ResponseErrorCodeEnum.CREDENTIAL_INVALID,
                        managementEntity.getWalletResponse().getErrorCode()));
        // Error & error code should be returned to wallet
        var responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(ResponseErrorCodeEnum.CREDENTIAL_INVALID.toString());
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

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, null, null);
        String presentationSubmission = emulator.getPresentationSubmissionString(UUID.randomUUID());

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", sdJWT))
                .andExpect(status().isOk());

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingSDJWTCredentialWithSD_thenSuccess() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, null, null);
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~";

        String presentationSubmission = emulator.getPresentationSubmissionString(UUID.randomUUID());

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", newCred))
                .andExpect(status().isOk()).andReturn();

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void twoTimesSameDisclosures_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, null, null);
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~" + StringUtils.join(sd, "~") + "~";

        String presentationSubmission = emulator.getPresentationSubmissionString(UUID.randomUUID());

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", newCred))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("errorDescription").value("Request contains non-distinct disclosures"));

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.FAILED,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void notYetValid_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(Instant.now().plus(7, ChronoUnit.DAYS).getEpochSecond(), null, null);
        String presentationSubmission = emulator.getPresentationSubmissionString(UUID.randomUUID());

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", sdJWT))
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

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond(), null);

        String presentationSubmission = emulator.getPresentationSubmissionString(UUID.randomUUID());

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", sdJWT))
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

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock(null, null, null);
        var additionalDisclosure = new Disclosure("additional", "definetly_wrong");

        String presentationSubmission = emulator.getPresentationSubmissionString(UUID.randomUUID());

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", sdJWT + additionalDisclosure + "~"))
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

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createNestedSDJWTMock(null, null);
        String presentationSubmission = emulator.getNestedPresentationSubmissionString(UUID.randomUUID());

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", sdJWT))
                .andExpect(status().isOk());

        var managementEntity = verificationManagementRepository.findById(requestId).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldVerifyingSDJWTCredentialSDWithDifferentPrivKey_thenException() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, null, new ECKeyGenerator(Curve.P_256).generate().toECPrivateKey());
        String presentationSubmission = emulator.getPresentationSubmissionString(UUID.randomUUID());

        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", sdJWT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("errorDescription").value("Signature mismatch"));
    }
}
