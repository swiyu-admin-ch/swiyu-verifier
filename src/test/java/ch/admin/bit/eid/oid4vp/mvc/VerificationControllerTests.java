package ch.admin.bit.eid.oid4vp.mvc;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.repository.PresentationDefinitionRepository;
import ch.admin.bit.eid.verifier_management.models.entities.ConstraintModel;
import ch.admin.bit.eid.verifier_management.models.entities.FieldsModel;
import ch.admin.bit.eid.verifier_management.models.entities.InputDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class VerificationControllerTests {
    @Autowired
    private MockMvc mock;
    @Autowired
    private PresentationDefinitionRepository  presentationDefinitionRepository;
    @Autowired
    private ApplicationConfiguration applicationConfiguration;



    private final static UUID offerId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    private final static UUID accessToken = UUID.fromString("deadbeef-1111-222-3333-deaddeafbeef");


    static PresentationDefinition createPresentaitonRequestion(){
        var  typeConstraint  = ConstraintModel.builder()
                .path(Arrays.asList("$.type"))
                .build();


        var inputDescriptor =  InputDescriptor.builder()
                .fields(FieldsModel.builder()
                        .fields(Arrays.asList(typeConstraint))
                        .build()).build();
        PresentationDefinition pd = PresentationDefinition.builder()
                .id(offerId)
                .inputDescriptors(Arrays.asList(inputDescriptor))
                .build();
        return pd;
    }

    @BeforeEach
    void setUp() {
        PresentationDefinition presentationRequest = createPresentaitonRequestion();
        presentationDefinitionRepository.save(presentationRequest);
    }

    @AfterEach
    void tearDown() {
        presentationDefinitionRepository.delete(presentationDefinitionRepository.findById(offerId).orElseThrow());
    }

    @Test
    void testRepository() throws Exception {

        var request = presentationDefinitionRepository.findById(offerId);
        assert request.orElseThrow().getId().equals(offerId);
    }

//    @Test
//    void shouldGetOpenIdConifugraion() throws Exception {
//        mock.perform(get("/.well-known/openid-configuration"))
//                .andExpect(status().isOk())
//                .andExpect(content().string(containsString("token_endpoint")))
//                .andExpect(content().string(not(containsString("${external-url}"))));
//    }
//
//    @Test
//    void shouldGetIssuerMetadata() throws Exception {
//        mock.perform(get("/.well-known/openid-credential-issuer"))
//                .andExpect(status().isOk())
//                .andExpect(content().string(not(containsString("${external-url}"))))
//                .andExpect(content().string(containsString("credential_endpoint")));
//    }
//
//    @Test
//    void testHappyPathCredentialFlow() throws Exception {
//        var response = mock.perform(post("/token")
//                        .param("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
//                        .param("pre-authorized_code", "deadbeef-dead-dead-dead-deaddeafbeef"))
//                .andExpect(status().isOk())
//                .andExpect(content().string(containsString("deadbeef")))
//                .andExpect(content().string(containsString("expires_in")))
//                .andExpect(content().string(containsString("access_token")))
//                .andExpect(content().string(containsString("BEARER")))
//                .andReturn();
//        var token_response = new ObjectMapper().readValue(response.getResponse().getContentAsString(), HashMap.class);
//        var token = token_response.get("access_token");
//        assert (accessToken.toString().equals(token));
//        response = mock.perform(post("/credential")
//                .header("Authorization", String.format("BEARER %s", token)))
//                .andExpect(status().isOk())
//                .andReturn();
//    }
//    @Test
//    void testTokenErrorInvalidPreAuthCode() throws Exception {
//        mock.perform(post("/token")
//                    .param("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
//                    .param("pre-authorized_code", "aaaaaaaa-dead-dead-dead-deaddeafdead"))
//                .andExpect(status().isBadRequest())
//                .andExpect(content().string(containsString("INVALID_GRANT")));
//    }
//
//    @Test
//    void testTokenErrorInvalidGrantType() throws Exception {
//        // With Valid preauth code
//        mock.perform(post("/token")
//                        .param("grant_type", "urn:ietf:params:oauth:grant-type:test-authorized_code")
//                        .param("pre-authorized_code", "deadbeef-dead-dead-dead-deaddeafbeef"))
//                .andExpect(status().isBadRequest())
//                .andExpect(content().string(containsString("INVALID_REQUEST")));
//
//        // With Invalid preauth code
//        mock.perform(post("/token")
//                        .param("grant_type", "urn:ietf:params:oauth:grant-type:test-authorized_code")
//                        .param("pre-authorized_code", "aaaaaaaa-dead-dead-dead-deaddeafdead"))
//                .andExpect(status().isBadRequest())
//                .andExpect(content().string(containsString("INVALID_REQUEST")));
//    }

}
