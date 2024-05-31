package ch.admin.bit.eid.oid4vp.mvc;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.util.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class VerificationControllerTests {
    @Autowired
    private MockMvc mock;
    @Autowired
    private VerificationManagementRepository verificationManagementRepository;
    @Autowired
    private ApplicationConfiguration applicationConfiguration;



    private final static UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    private final static UUID accessToken = UUID.fromString("deadbeef-1111-222-3333-deaddeafbeef");


    static PresentationDefinition createPresentationRequest(){

        HashMap<String, Object> fields = new HashMap<>() {{
            put("path", Arrays.asList("$.type", "$.credentialSubject.test"));
        }};

        InputDescriptor inputDescriptor = InputDescriptor.builder()
            .id("test_descriptor")
            .name("Test Descriptor")
            .constraints(
                    new HashMap<>() {{
                        put("fields", new HashSet() {{
                            add(fields);
                        }});
                    }}
            )
            .build();
        PresentationDefinition pd = PresentationDefinition.builder()
                .id(requestId)
                .inputDescriptors(Arrays.asList(inputDescriptor))
                .build();
        return pd;
    }



    static ManagementEntity createTestManagementEntity() {
        return ManagementEntity.builder()
                .id(requestId.toString())
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
    void shouldGetRequest() throws Exception {
        var response = mock.perform(get(String.format("/request-object/%s", requestId.toString())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("client_id")))
                .andExpect(content().string(containsString("client_id_scheme\":\"did\"")))
                .andExpect(content().string(containsString("client_id\":\"did:")))
                .andExpect(content().string(containsString("test_descriptor")))
                .andExpect(content().string(containsString("Test Descriptor")))
                .andExpect(content().string(not(containsString("${external-url}"))))
                .andExpect(content().string(not(containsString("null"))))
                .andReturn();

        System.out.println(response);

    }
}
