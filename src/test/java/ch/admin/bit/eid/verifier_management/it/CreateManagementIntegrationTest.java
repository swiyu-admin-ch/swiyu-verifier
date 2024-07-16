package ch.admin.bit.eid.verifier_management.it;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
    void testCreateOffer_thenSuccess() throws Exception {
        String test = """
        {
            "id":"string",
            "name":"string",
            "purpose":"string",
            "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
            "input_descriptors":[{
                "id":"string",
                "name":"string",
                "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
                "constraints":[{
                    "fields":[{
                        "path":["$.teest"],
                        "id":"string",
                        "name":"string",
                        "purpose":"string"
                    }]
                }]
            }]
        }
        """;

        MvcResult result = mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(test))
                .andExpect(status().isOk())

                // check management dto
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatusEnum.PENDING.toString()))
                .andExpect(jsonPath("$.verification_url").isNotEmpty())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        MvcResult result1  = mvc.perform(get("/verifications/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatusEnum.PENDING.toString()))
                .andExpect(jsonPath("$.verification_url").isNotEmpty())
                .andReturn();

        result1.getResponse();
    }

    @Test
    void testCreateOfferValidation_noInputDescriptor_thenException() throws Exception {
        String noInputDescriptorId = """
                {
                    "id":"string",
                    "name":"string",
                    "purpose":"string",
                    "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
                    "input_descriptors":[{
                        "name":"string",
                        "constraints":[{
                            "fields":[{
                                "path":["$.teest"],
                                "id":"string",
                                "name":"string",
                                "purpose":"string"
                            }]
                        }]
                    }]
                }
                """;
        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noInputDescriptorId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_noConstraints_thenException() throws Exception {
        String noConstraints = """
                {
                    "id":"string",
                    "name":"string",
                    "purpose":"string",
                    "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
                    "input_descriptors":[{
                        "id":"string",
                        "name":"string"
                    }]
                }
                """;
        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noConstraints))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_emptyConstraints_thenException() throws Exception {

        String emptyConstraints = """
                {
                    "id":"string",
                    "name":"string",
                    "purpose":"string",
                    "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
                    "input_descriptors":[{
                        "id":"string",
                        "name":"string",
                        "constraints":[]
                        }]
                    }]
                }
                """;
        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyConstraints))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_noFieldPath_thenException() throws Exception {
        String noFieldPath = """
                {
                    "id":"string",
                    "name":"string",
                    "purpose":"string",
                    "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
                    "input_descriptors":[{
                        "name":"string",
                        "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
                        "constraints":[{
                            "fields":[{
                                "id":"string",
                                "name":"string",
                                "purpose":"string"
                            }]
                        }]
                    }]
                }
                """;
        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noFieldPath))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_emptyFieldPath_thenException() throws Exception {
        String emptyFieldPath = """
        {
            "id":"string",
            "name":"string",
            "purpose":"string",
            "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
            "input_descriptors":[{
                "name":"string",
                "format": {"ldp_vp": {"proof_type":["BBS-2023"]}},
                "constraints":[{
                    "fields":[]
                }]
            }]
        }
        """;
        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyFieldPath))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Invalid request content."))
                .andReturn();
    }

    @Test
    void testCreateMinimalExample_thenSuccess() throws Exception {
        String emptyFieldPath = """
        {
            "input_descriptors":[{
                "id":"string",
                "constraints":[{
                    "fields":[{
                        "path":["string"]
                    }]
                }]
            }]
        }
        """;

        MvcResult result = mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyFieldPath))
                .andExpect(status().isOk())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        MvcResult result1  = mvc.perform(get("/verifications/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(-1, result1.getResponse().getContentAsString().indexOf("null"));
    }

    @Test
    void testCreateCompleteExample_thenSuccess() throws Exception {
        String test = """
        {
            "id":"string",
            "name":"string",
            "purpose":"string",
            "format": {
                "ldp_vp": {
                    "proof_type":["BBS-2023"]
                }
            },
            "input_descriptors":[{
                "id":"inputDescriptors_id",
                "name":"inputDescriptors_name",
                "constraints":[{
                    "fields":[{
                        "path":["$.constraints_path_1","$.constraints_path_2"],
                        "id":"field_id",
                        "name":"field_name",
                        "purpose":"field_purpose"
                    }]
                }]
            }]
        }
        """;
        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(test))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatusEnum.PENDING.toString()))
                .andExpect(jsonPath("$.presentation_definition.id").isNotEmpty())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors.length()").value(1))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].id").value("inputDescriptors_id"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].name").value("inputDescriptors_name"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.length()").value(1))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints[0].fields").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints[0].fields[0].path").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints[0].fields[0].path.length()").value(2))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints[0].fields[0].path[0]").value("$.constraints_path_1"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints[0].fields[0].path[1]").value("$.constraints_path_2"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints[0].fields[0].id").value("field_id"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints[0].fields[0].name").value("field_name"))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints[0].fields[0].purpose").value("field_purpose"))
                .andReturn();
    }
}
