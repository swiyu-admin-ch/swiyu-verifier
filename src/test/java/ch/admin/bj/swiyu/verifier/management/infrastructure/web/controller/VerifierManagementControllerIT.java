/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.infrastructure.web.controller;

import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ApiFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.admin.bj.swiyu.verifier.management.api.definition.FieldDto;
import ch.admin.bj.swiyu.verifier.management.api.definition.FormatAlgorithmDto;
import ch.admin.bj.swiyu.verifier.management.domain.management.VerificationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(connection = EmbeddedDatabaseConnection.H2)
class VerifierManagementControllerIT {

    private static final String BASE_URL = "/api/v1/verifications";
    @Autowired
    protected MockMvc mvc;

    @Test
    void testCreateOffer_thenSuccess() throws Exception {

        var request = createVerificationManagementDto();

        var sdJWTFormatType = "vc+sd-jwt";

        var reqDescriptor0 = request.presentationDefinition().inputDescriptors().getFirst();
        var reqField0 = reqDescriptor0.constraints().fields().getFirst();
        var sdJwtFormat = reqDescriptor0.format().get(sdJWTFormatType);

        MvcResult result = mvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())

                // check management dto
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatus.PENDING.toString()))
                .andExpect(jsonPath("$.verification_url").isNotEmpty())

                .andExpect(jsonPath("$.presentation_definition.id").isNotEmpty())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors.length()").value(1))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].id").value(reqDescriptor0.id()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].name").value(reqDescriptor0.name()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints").isNotEmpty())

                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].format.%s.sd-jwt_alg_values[0]".formatted(sdJWTFormatType)).value(sdJwtFormat.alg().getFirst()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].format.%s.kb-jwt_alg_values[0]".formatted(sdJWTFormatType)).value(sdJwtFormat.keyBindingAlg().getFirst()))

                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].id").value(reqField0.id()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].name").value(reqField0.name()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].purpose").value(reqField0.purpose()))

                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path").isArray())
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path.length()").value(2))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path[0]").value(reqField0.path().getFirst()))
                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints.fields[0].path[1]").value(reqField0.path().get(1)))
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        MvcResult result1 = mvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                .andExpect(jsonPath("$.state").value(VerificationStatus.PENDING.toString()))
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
        var request = createVerificationManagementDto();
        request.presentationDefinition().inputDescriptors().clear();
        request.presentationDefinition().inputDescriptors().add(inputDescriptorDto(null));

        mvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("presentationDefinition.inputDescriptors[0].id: id of input descriptor is mandatory"))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_noConstraints_thenException() throws Exception {
        var request = createVerificationManagementDto();
        request.presentationDefinition().inputDescriptors().clear();
        request.presentationDefinition().inputDescriptors().add(inputDescriptorDto_WithoutConstraints());

        mvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("presentationDefinition.inputDescriptors[0].constraints: must not be null"))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_noFieldPath_thenException() throws Exception {
        // GIVEN
        var request = createVerificationManagementDto();
        var constraints = request.presentationDefinition().inputDescriptors().getFirst().constraints();
        constraints.fields().clear();
        constraints.fields().add(new FieldDto(null, null, null, null, null));
        // WHEN / THEN
        mvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("presentationDefinition.inputDescriptors[0].constraints.fields[0].path: must not be empty"))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_emptyFieldPath_thenException() throws Exception {
        // GIVEN
        var request = createVerificationManagementDto();
        var constraints = request.presentationDefinition().inputDescriptors().getFirst().constraints();
        constraints.fields().clear();

        // WHEN / THEN
        mvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("presentationDefinition.inputDescriptors[0].constraints.fields: must not be empty"))
                .andReturn();
    }

    @Test
    void testCreateOfferValidation_withInvalidAlgorithmFormats_thenExceptionWithMultipleErrors() throws Exception {
        // GIVEN
        var request = createVerificationManagementDto();
        request.presentationDefinition().inputDescriptors().clear();
        request.presentationDefinition().inputDescriptors().add(inputDescriptorDto_Invalid());
        request.presentationDefinition().format().clear();
        request.presentationDefinition().format().put("FailCrypt", new FormatAlgorithmDto(null, null));

        // WHEN / THEN
        var expectedPresentationFormatError = "presentationDefinition.format: Invalid format";
        var expectedInputDescriptorFormatError = "presentationDefinition.inputDescriptors[0].format: Invalid format";

        mvc.perform(post("/api/v1/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value(expectedPresentationFormatError + ", " + expectedInputDescriptorFormatError))
                .andReturn();
    }

    @Test
    void testCreateMinimalExample_thenSuccess() throws Exception {
        var minimal = createVerificationManagementDto_Minimal(true);
        MvcResult result = mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(minimal)))
                .andExpect(status().isOk())
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        MvcResult result1 = mvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(-1, result1.getResponse().getContentAsString().indexOf("null"));

        MvcResult result2 = mvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        assertEquals(-1, result2.getResponse().getContentAsString().indexOf("null"));
    }
}
