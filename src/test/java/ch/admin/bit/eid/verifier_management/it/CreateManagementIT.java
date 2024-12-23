package ch.admin.bit.eid.verifier_management.it;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.mocks.VerificationRequestMock;
import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CreateManagementIT {

    @Autowired
    protected MockMvc mvc;

    @Test
    void testCreateOffer_thenSuccess() throws Exception {

        var request = VerificationRequestMock.create();

        var sdJWTFormatType = "vc+sd-jwt";

        var reqDescriptor0 = request.getPresentationDefinition().getInputDescriptors().getFirst();
        var reqField0 = reqDescriptor0.getConstraints().getFields().getFirst();
        var sdJwtFormat = reqDescriptor0.getFormat().get(sdJWTFormatType);

        MvcResult result = mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())

                // check management dto
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatusEnum.PENDING.toString()))
                .andExpect(jsonPath("$.verification_url").isNotEmpty())

                .andExpect(jsonPath("$.presentation_definition.id").isNotEmpty())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors.length()").value(1))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].id").value(reqDescriptor0.getId()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].name").value(reqDescriptor0.getName()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints").isNotEmpty())

                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].format.%s.sd-jwt_alg_values[0]".formatted(sdJWTFormatType)).value(sdJwtFormat.getAlg().getFirst()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].format.%s.kb-jwt_alg_values[0]".formatted(sdJWTFormatType)).value(sdJwtFormat.getKeyBindingAlg().getFirst()))

                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].id").value(reqField0.getId()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].name").value(reqField0.getName()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].purpose").value(reqField0.getPurpose()))

                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path.length()").value(2))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path[0]").value(reqField0.getPath().getFirst()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path[1]").value(reqField0.getPath().get(1)))
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        MvcResult result1 = mvc.perform(get("/verifications/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatusEnum.PENDING.toString()))
                .andExpect(jsonPath("$.verification_url").isNotEmpty())
                /* TODO check seems to be working on swagger but not on it
                .andExpect(jsonPath(inputDescriptor0JsonPath + ".id").value(expectedJsonContext.read(reqDescriptor0JsonPath +".id").toString()))
                .andExpect(jsonPath(inputDescriptor0JsonPath + ".name").value(expectedJsonContext.read(reqDescriptor0JsonPath + ".name").toString()))
                .andExpect(jsonPath(proofPath).value(expectedJsonContext.read(reqProofJsonPath).toString()))
                .andExpect(jsonPath(fieldsPath + ".id").value(expectedJsonContext.read(reqField0JsonPath + ".id").toString()))
                .andExpect(jsonPath(fieldsPath + ".name").value(expectedJsonContext.read(reqField0JsonPath + ".name").toString()))
                .andExpect(jsonPath(fieldsPath + ".purpose").value(expectedJsonContext.read(reqField0JsonPath + ".purpose").toString()))
                .andExpect(jsonPath(fieldsPath + ".path[0]").value(expectedJsonContext.read(reqField0JsonPath + ".path[0]").toString()))
                 */
                .andReturn();

        result1.getResponse();
    }

    @Test
    void testCreateOfferValidation_noInputDescriptorId_thenException() throws Exception {
        var request = VerificationRequestMock.create();
        var inputDescriptor = request.getPresentationDefinition().getInputDescriptors().getFirst();
        inputDescriptor.setId(null);
        request.getPresentationDefinition().setInputDescriptors(List.of(inputDescriptor));

        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("presentationDefinition.inputDescriptors[0].id: Input descriptor is mandatory"))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_noConstraints_thenException() throws Exception {
        var request = VerificationRequestMock.create();
        var inputDescriptor = request.getPresentationDefinition().getInputDescriptors().getFirst();
        inputDescriptor.setConstraints(null);
        request.getPresentationDefinition().setInputDescriptors(List.of(inputDescriptor));

        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("presentationDefinition.inputDescriptors[0].constraints: must not be null"))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_noFieldPath_thenException() throws Exception {

        var request = VerificationRequestMock.create();
        var constraints = request.getPresentationDefinition().getInputDescriptors().getFirst().getConstraints();
        var newField = constraints.getFields().getFirst().toBuilder().path(null).build();
        constraints.setFields(List.of(newField));

        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("presentationDefinition.inputDescriptors[0].constraints.fields[0].path: must not be empty"))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_emptyFieldPath_thenException() throws Exception {

        var request = VerificationRequestMock.create();
        var constraints = request.getPresentationDefinition().getInputDescriptors().getFirst().getConstraints();
        constraints.setFields(List.of());

        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("presentationDefinition.inputDescriptors[0].constraints.fields: must not be empty"))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_withInvalidAlgorithmFormats_thenExceptionWithMultipleErrors() throws Exception {

        var request = VerificationRequestMock.create();

        request.getPresentationDefinition().setFormat(Map.of("FailCrypt", new FormatAlgorithmDto()));
        request.getPresentationDefinition().getInputDescriptors().getFirst().setFormat(Map.of("WeakCrypt", new FormatAlgorithmDto()));

        var expectedPresentationFormatError = "presentationDefinition.format: Invalid format";
        var expectedInputDescriptorFormatError = "presentationDefinition.inputDescriptors[0].format: Invalid format";

        mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value(expectedPresentationFormatError + ", " + expectedInputDescriptorFormatError))
                .andReturn();
    }

    @Test
    void testCreateMinimalExample_thenSuccess() throws Exception {
        var minimal = VerificationRequestMock.createMinimal(true);
        MvcResult result = mvc.perform(post("/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(minimal)))
                .andExpect(status().isOk())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        MvcResult result1 = mvc.perform(get("/verifications/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(-1, result1.getResponse().getContentAsString().indexOf("null"));
    }
}
