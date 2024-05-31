package ch.admin.bit.eid.verifier_management.it;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class VerifierManagementIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Container
    private static RedisContainer REDIS_CONTAINER = new RedisContainer(
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
    void testCreateOffer_thenSuccess() throws Exception {
        String test = "{\"id\": \"32f54163-7166-48f1-93d8-ff217bdb0653\",\"input_descriptors\": [{\"id\": \"driver_license\",\"name\": \"Driver License\",\"constraints\": {\"fields\": [{\"path\": [\"$.credentialSubject.dateOfBirth\",\"$.vc.credentialSubject.dateOfBirth\",\"$.vc.credentialSubject.dob\"]}]}}]}";

        /*
        "expires_at": 0,
  "id": "string",
  "authorization_request_object_uri": "string",
  "authorization_request_id": "string",
  "status": "PENDING"
         */

        mvc.perform(post("/verifications").contentType(MediaType.APPLICATION_JSON).content(test))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.authorization_request_id").isNotEmpty())
                .andExpect(jsonPath("$.authorization_request_object_uri").isNotEmpty())
                .andExpect(jsonPath("$.status").value(VerificationStatusEnum.PENDING.toString()))
                .andExpect(jsonPath("$.expires_at").isNotEmpty());
    }
}
