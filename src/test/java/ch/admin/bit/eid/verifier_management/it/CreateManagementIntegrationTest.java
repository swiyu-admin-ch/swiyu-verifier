package ch.admin.bit.eid.verifier_management.it;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import com.jayway.jsonpath.JsonPath;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CreateManagementIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Container
    private static final RedisContainer REDIS_CONTAINER = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG)).withExposedPorts(6379);

    @DynamicPropertySource
    private static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379).toString());
    }

    @Test
    void givenRedisContainerConfiguredWithDynamicProperties_whenCheckingRunningStatus_thenStatusIsRunning() {
        assertTrue(REDIS_CONTAINER.isRunning());
    }

    @Test
    void testCreateOffer_thenUnauthorized() throws Exception {
        String test = "{\"inputDescriptors\": [{\"id\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"name\": \"string\",\"group\": [\"string\"],\"format\": {\"formatProp\": {\"test\": \"test\"}},\"constraints\": {\"constraintsProp\": {\"test\": \"test\"}}}],\"credentialSubjectData\": {\"credentialSubjectDataProp\": {\"test\": \"test\"}},\"submissionRequirements\": {\"submissionRequirementsProp\": {\"test\": \"test\"}}}";

        mvc.perform(post("/verifications")
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(test))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void testCreateOffer_thenSuccess() throws Exception {
        String test = "{\"inputDescriptors\": [{\"id\": \"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"name\": \"string\",\"group\": [\"string\"],\"format\": {\"formatProp\": {\"test\": \"test\"}},\"constraints\": {\"constraintsProp\": {\"test\": \"test\"}}}],\"credentialSubjectData\": {\"credentialSubjectDataProp\": {\"test\": \"test\"}},\"submissionRequirements\": {\"submissionRequirementsProp\": {\"test\": \"test\"}}}";

        MvcResult result = mvc.perform(post("/verifications")
                        .with(SecurityMockMvcRequestPostProcessors.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(test))
                .andExpect(status().isOk())

                // check management dto
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.requestNonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatusEnum.PENDING.toString()))
                .andExpect(jsonPath("$.verificationUrl").isNotEmpty())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        MvcResult result1  = mvc.perform(get("/verifications/" + id).with(SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.requestNonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatusEnum.PENDING.toString()))
                .andExpect(jsonPath("$.verificationUrl").isNotEmpty())
                .andReturn();

        result1.getResponse();

    }
}
