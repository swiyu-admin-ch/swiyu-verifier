package ch.admin.bit.eid.oid4vp.mvc;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.mock.CredentialEmulator;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.util.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class VerificationControllerTests {
    @Autowired
    private MockMvc mock;
    @Autowired
    private VerificationManagementRepository verificationManagementRepository;
    @Autowired
    private ApplicationConfiguration applicationConfiguration;

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


    static PresentationDefinition createPresentationRequest(){

        HashMap<String, Object> fields = new HashMap<>() {{
            put("path", Arrays.asList("$.type", "$.credentialSubject.test"));
        }};

//        InputDescriptor inputDescriptor = InputDescriptor.builder()
//            .id("test_descriptor")
//            .name("Test Descriptor")
//            .constraints(
//                    new HashMap<>() {{
//                        put("fields", new HashSet() {{
//                            add(fields);
//                        }});
//                    }}
//            )
//            .build();
        PresentationDefinition pd = PresentationDefinition.builder()
                .id(requestId)
                //.inputDescriptors(Arrays.asList(inputDescriptor))
                .build();
        return pd;
    }



    static ManagementEntity createTestManagementEntity() {
        return ManagementEntity.builder()
                .id(requestId)
                .requestedPresentation(createPresentationRequest())
                .state(VerificationStatusEnum.PENDING)
                .requestNonce("HelloNonce")
                .build();
    }

    @BeforeEach
    void setUp() {
        ManagementEntity entity = createTestManagementEntity();
        verificationManagementRepository.save(entity);
    }

    @AfterEach
    void tearDown() {
        verificationManagementRepository.delete(verificationManagementRepository.findById(requestId.toString()).orElseThrow());
    }

    @Test
    void testRepository() throws Exception {

        var request = verificationManagementRepository.findById(requestId.toString());
        assert request.orElseThrow().getId().equals(requestId.toString());
    }

    @Test
    void shouldGetRequestObject() throws Exception {
        var response = mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("client_id")))
                .andExpect(content().string(containsString("client_id_scheme\":\"did\"")))
                .andExpect(content().string(containsString("client_id\":\"did:")))
                .andExpect(content().string(containsString("test_descriptor")))
                .andExpect(content().string(containsString("Test Descriptor")))
                .andExpect(content().string(not(containsString("${external-url}"))))
                .andExpect(content().string(containsString(requestId.toString())))
                .andExpect(content().string(not(containsString("null"))))
                .andReturn();

    }

    @Test
    void shouldAcceptRefusal() throws Exception {
        var response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .formField("error", "I_dont_want_to")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isOk())
                .andReturn();
        var managementEntity = verificationManagementRepository.findById(requestId.toString()).orElseThrow();
        assert managementEntity.getState() == VerificationStatusEnum.FAILED;
    }

    /**
     * The Verification of the Credential should not succeed because there is no VDR
     */
    @Test
    void shouldSucceedVerifyingCredential() throws Exception {
        var emulator =  new CredentialEmulator();

        var credential = emulator.createVC(
                Arrays.asList("/type", "/issuer"),
                "{\"issuer\": \"did:example:12345\", \"type\": [\"VerifiableCredential\", \"ExampleCredential\"], \"credentialSubject\": {\"hello\": \"world\"}}");

        // Fetch the Request Object
        var response = mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andReturn();
        // Get the Nonce
        JsonObject responseContent = JsonParser.parseString(response.getResponse().getContentAsString()).getAsJsonObject();
        String nonce = responseContent.get("nonce").getAsString();
        String vpToken = emulator.createVerifiablePresentation(credential, Arrays.asList("/credentialSubject/hello"), nonce);
        String presentationSubmission = emulator.createCredentialSubmission();
        response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                .formField("presentation_submission", presentationSubmission)
                .formField("vp_token", vpToken))
            .andExpect(status().isOk())
            .andReturn();

        var managementEntity = verificationManagementRepository.findById(requestId.toString()).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        assert managementEntity.getWalletResponse().getCredentialSubjectData().contains("world")  == true;

        // Test Resending after Success

        response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        //Data should be unchanged
        managementEntity = verificationManagementRepository.findById(requestId.toString()).orElseThrow();
        Assert.state(managementEntity.getState() == VerificationStatusEnum.SUCCESS,
                String.format("Expecting state to be failed, but got %s", managementEntity.getState()));
        assert managementEntity.getWalletResponse().getCredentialSubjectData().contains("world")  == true;

        // Error that verification is closed should be returned to wallet
        var responseBody = response.getResponse().getContentAsString();
        Assert.hasText(response.getResponse().getContentAsString(), "Should have response body");
        assert responseBody.contains(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED.toString());
    }

    @Test
    void shouldFailVerifyingCredentialWrongNonce() throws Exception {
        var emulator =  new CredentialEmulator();

        var credential = emulator.createVC(
                Arrays.asList("/type", "/issuer"),
                "{\"issuer\": \"did:example:12345\", \"type\": [\"VerifiableCredential\", \"ExampleCredential\"], \"credentialSubject\": {\"hello\": \"world\"}}");

        // Fetch the Request Object
        var response = mock.perform(get(String.format("/request-object/%s", requestId)))
                .andExpect(status().isOk())
                .andReturn();
        // Get the Nonce
        JsonObject responseContent = JsonParser.parseString(response.getResponse().getContentAsString()).getAsJsonObject();
//        String nonce = responseContent.get("nonce").getAsString();
        String nonce = "wrong_nonce";
        String vpToken = emulator.createVerifiablePresentation(credential, Arrays.asList("/credentialSubject/hello"), nonce);
        String presentationSubmission = emulator.createCredentialSubmission();
        response = mock.perform(post(String.format("/request-object/%s/response-data", requestId))
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
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
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
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
                .andExpect(content().string(containsString(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND.toString())));
    }

}
