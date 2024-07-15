package ch.admin.bit.eid.oid4vp.mvc;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.mock.CredentialEmulator;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.Assert;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static ch.admin.bit.eid.oid4vp.mock.CredentialEmulator.ExampleJson;
import static ch.admin.bit.eid.oid4vp.mock.ManagementEntityMock.getManagementEntityMock;
import static ch.admin.bit.eid.oid4vp.mock.PresentationDefinitionMocks.createPresentationDefinitionMock;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


import java.nio.charset.StandardCharsets;
import java.util.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificationControllerTests {

    @Autowired
    private MockMvc mock;
    @Autowired
    private VerificationManagementRepository verificationManagementRepository;
    @Autowired
    private ApplicationConfiguration applicationConfiguration;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CredentialEmulator emulator =  new CredentialEmulator();;

    @Container
    private static final RedisContainer REDIS_CONTAINER = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG)
    ).withExposedPorts(6379);

    @DynamicPropertySource
    private static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379).toString());
    }

    private final static UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    private final static UUID accessToken = UUID.fromString("deadbeef-1111-222-3333-deaddeafbeef");

    @BeforeEach
    void setUp() {
        ManagementEntity entity = getManagementEntityMock(requestId,
                createPresentationDefinitionMock(requestId, List.of("$.hello")));
        verificationManagementRepository.save(entity);
    }

    @AfterEach
    void tearDown() {
        verificationManagementRepository.delete(verificationManagementRepository.findById(requestId.toString()).orElseThrow());
    }

    @Test
    void shouldGetRequestObject() throws Exception {
        ManagementEntity entity = getManagementEntityMock(requestId, createPresentationDefinitionMock(requestId, List.of("$.hello")));
        InputDescriptor inputDescriptor = entity.getRequestedPresentation().getInputDescriptors().getFirst();
        mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value(applicationConfiguration.getClientId()))
                .andExpect(jsonPath("$.client_id_scheme").value(applicationConfiguration.getClientIdScheme()))
                .andExpect(jsonPath("$.response_type").value("vp_token"))
                .andExpect(jsonPath("$.response_mode").value("direct_post"))
                .andExpect(jsonPath("$.nonce").isNotEmpty())
                .andExpect(jsonPath("$.response_uri").value(String.format("%s/request-object/%s/response-data", applicationConfiguration.getExternalUrl(), entity.getId())))
                .andExpect(jsonPath("$.presentation_definition[0].id").value(inputDescriptor.getId()))
                .andExpect(jsonPath("$.presentation_definition[0].name").value(inputDescriptor.getName()))
                .andExpect(jsonPath("client_metadata.client_name").value(applicationConfiguration.getClientName()))
                .andExpect(jsonPath("client_metadata.logo_uri").value(applicationConfiguration.getLogoUri()))

                .andExpect(content().string(not(containsString("null")))).andReturn();
    }

    @Test
    void shouldAcceptRefusal() throws Exception {
        mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .formField("error", "I_dont_want_to")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isOk());
        var managementEntity = verificationManagementRepository.findById(requestId.toString()).orElseThrow();
        assert managementEntity.getState() == VerificationStatusEnum.FAILED;
    }

    /**
     * The Verification of the Credential should not succeed because there is no VDR
     */
    @Test
    void shouldSucceedVerifyingCredential() throws Exception {

        var credential = emulator.createVC(
                List.of("/type", "/issuer"),
                ExampleJson);

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

        var managementEntity = verificationManagementRepository.findById(requestId.toString()).orElseThrow();
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
        managementEntity = verificationManagementRepository.findById(requestId.toString()).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        assert managementEntity.getWalletResponse().getCredentialSubjectData().contains("world");

        // Error that verification is closed should be returned to wallet
        var responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED.toString());
    }

    @Test
    void shouldFailOnInvalidPresentationSubmissionWithAdditionallyEncodedString() throws Exception {

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
    void shouldFailOnInvalidPresentationSubmissionWithInvalidJson() throws Exception {

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
    void shouldFailVerifyingCredentialWrongNonce() throws Exception {

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

        var managementEntity = verificationManagementRepository.findById(requestId.toString()).orElseThrow();
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
    void shouldRespond404() throws Exception {
        UUID notExistingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        mock.perform(get(String.format("/request-object/%s", notExistingRequestId)))
                .andExpect(status().isNotFound());

        mock.perform(post(String.format("/request-object/%s/response-data", notExistingRequestId))
                    .formField("error", "trying_to_get_404")
                    .formField("error_description", ""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND.toString()));
    }
}
