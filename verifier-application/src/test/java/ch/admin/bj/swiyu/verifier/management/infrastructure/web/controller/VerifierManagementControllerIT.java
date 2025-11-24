/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.api.definition.FieldDto;
import ch.admin.bj.swiyu.verifier.api.definition.FormatAlgorithmDto;
import ch.admin.bj.swiyu.verifier.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.api.management.TrustAnchorDto;
import ch.admin.bj.swiyu.verifier.api.management.dcql.DcqlClaimDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.management.test.fixtures.ApiFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ApiFixtures.*;
import static ch.admin.bj.swiyu.verifier.management.test.fixtures.ApiFixtures.presentationDefinitionDto;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Transactional
class VerifierManagementControllerIT {

        private static final String BASE_URL = "/management/api/verifications";
        @Autowired
        protected MockMvc mvc;

        @Autowired
        private ApplicationProperties applicationProperties;

        private List<String> issuerDids = List.of(UUID.randomUUID().toString());

        @Test
        void testCreateOffer_thenSuccess() throws Exception {
                var request = createVerificationManagementDto(issuerDids);
                var sdJWTFormatType = "vc+sd-jwt";

                var reqDescriptor0 = request.presentationDefinition().inputDescriptors().getFirst();
                var reqField0 = reqDescriptor0.constraints().fields().getFirst();
                var sdJwtFormat = reqDescriptor0.format().get(sdJWTFormatType);

                MvcResult result = mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isOk())

                                // check management dto
                                .andExpect(jsonPath("$.id").isNotEmpty())
                                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                                .andExpect(jsonPath("$.state").value(VerificationStatus.PENDING.toString()))
                                .andExpect(jsonPath("$.verification_url").isNotEmpty())
                                // must contain deeplink schema
                                .andExpect(jsonPath("$.verification_deeplink",
                                                containsString(applicationProperties.getDeeplinkSchema())))
                                // must contain did as client id in deeplink url
                                .andExpect(jsonPath("$.verification_deeplink",
                                                containsString(URLEncoder.encode(applicationProperties.getClientId(),
                                                                StandardCharsets.UTF_8))))

                                .andExpect(jsonPath("$.presentation_definition.id").isNotEmpty())
                                .andExpect(jsonPath("$.presentation_definition.input_descriptors").isArray())
                                .andExpect(jsonPath("$.presentation_definition.input_descriptors.length()").value(1))
                                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].id")
                                                .value(reqDescriptor0.id()))
                                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].name")
                                                .value(reqDescriptor0.name()))
                                .andExpect(jsonPath("$.presentation_definition.input_descriptors[0].constraints")
                                                .isNotEmpty())

                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].format.%s.sd-jwt_alg_values[0]"
                                                                .formatted(sdJWTFormatType))
                                                .value(sdJwtFormat.alg().getFirst()))
                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].format.%s.kb-jwt_alg_values[0]"
                                                                .formatted(sdJWTFormatType))
                                                .value(sdJwtFormat.keyBindingAlg().getFirst()))

                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].constraints.fields[0].id")
                                                .value(reqField0.id()))
                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].constraints.fields[0].name")
                                                .value(reqField0.name()))
                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].constraints.fields[0].purpose")
                                                .value(reqField0.purpose()))

                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].constraints.fields[0].path")
                                                .isArray())
                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].constraints.fields[0].path.length()")
                                                .value(2))
                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].constraints.fields[0].path[0]")
                                                .value(reqField0.path().getFirst()))
                                .andExpect(jsonPath(
                                                "$.presentation_definition.input_descriptors[0].constraints.fields[0].path[1]")
                                                .value(reqField0.path().get(1)))
                                .andReturn();

                String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

                MvcResult result1 = mvc.perform(get(BASE_URL + "/" + id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").isNotEmpty())
                                .andExpect(jsonPath("$.request_nonce").isNotEmpty())
                                .andExpect(jsonPath("$.state").value(VerificationStatus.PENDING.toString()))
                                .andExpect(jsonPath("$.verification_url").isNotEmpty())
                                /*
                                 * TODO check seems to be working on swagger but not on it
                                 * .andExpect(jsonPath(inputDescriptor0JsonPath +
                                 * ".id").value(expectedJsonContext.read(reqDescriptor0JsonPath
                                 * +".id").toString()))
                                 * .andExpect(jsonPath(inputDescriptor0JsonPath +
                                 * ".name").value(expectedJsonContext.read(reqDescriptor0JsonPath +
                                 * ".name").toString()))
                                 * .andExpect(jsonPath(proofPath).value(expectedJsonContext.read(
                                 * reqProofJsonPath).toString()))
                                 * .andExpect(jsonPath(fieldsPath +
                                 * ".id").value(expectedJsonContext.read(reqField0JsonPath + ".id").toString()))
                                 * .andExpect(jsonPath(fieldsPath +
                                 * ".name").value(expectedJsonContext.read(reqField0JsonPath +
                                 * ".name").toString()))
                                 * .andExpect(jsonPath(fieldsPath +
                                 * ".purpose").value(expectedJsonContext.read(reqField0JsonPath +
                                 * ".purpose").toString()))
                                 * .andExpect(jsonPath(fieldsPath +
                                 * ".path[0]").value(expectedJsonContext.read(reqField0JsonPath +
                                 * ".path[0]").toString()))
                                 */
                                .andReturn();

                result1.getResponse();
        }

        @Test
        void testCreateOfferValidation_noInputDescriptorId_thenException() throws Exception {
                var request = createVerificationManagementDto(null);
                request.presentationDefinition().inputDescriptors().clear();
                request.presentationDefinition().inputDescriptors().add(inputDescriptorDto(null));

                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error_description").value(
                                                "presentationDefinition.inputDescriptors[0].id: id of input descriptor is mandatory"))
                                .andReturn();
        }

        @Test
        void testCreateOfferValidation_noConstraints_thenException() throws Exception {
                var request = createVerificationManagementDto(null);
                request.presentationDefinition().inputDescriptors().clear();
                request.presentationDefinition().inputDescriptors().add(inputDescriptorDto_WithoutConstraints());

                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error_description").value(
                                                "presentationDefinition.inputDescriptors[0].constraints: must not be null"))
                                .andReturn();
        }

        @Test
        void testCreateOfferValidation_noFieldPath_thenException() throws Exception {
                // GIVEN
                var request = createVerificationManagementDto(null);
                var constraints = request.presentationDefinition().inputDescriptors().getFirst().constraints();
                var emptyFieldDto = FieldDto.builder().build();
                constraints.fields().clear();
                constraints.fields().add(emptyFieldDto);
                // WHEN / THEN
                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error_description").value(
                                                "presentationDefinition.inputDescriptors[0].constraints.fields[0].path: must not be empty"))
                                .andReturn();
        }

        @Test
        void testCreateOfferValidation_emptyFieldPath_thenException() throws Exception {
                // GIVEN
                var request = createVerificationManagementDto(null);
                var constraints = request.presentationDefinition().inputDescriptors().getFirst().constraints();
                constraints.fields().clear();

                // WHEN / THEN
                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error_description").value(
                                                "presentationDefinition.inputDescriptors[0].constraints.fields: must not be empty"))
                                .andReturn();
        }

        @Test
        void testCreateOfferValidation_withInvalidAlgorithmFormats_thenExceptionWithMultipleErrors() throws Exception {
                // GIVEN
                var request = createVerificationManagementDto(null);
                request.presentationDefinition().inputDescriptors().clear();
                request.presentationDefinition().inputDescriptors().add(inputDescriptorDto_Invalid());
                request.presentationDefinition().format().clear();
                request.presentationDefinition().format().put("FailCrypt", new FormatAlgorithmDto(null, null));

                // WHEN / THEN
                var expectedPresentationFormatError = "presentationDefinition.format: Invalid format";
                var expectedInputDescriptorFormatError = "presentationDefinition.inputDescriptors[0].format: Invalid format";

                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error_description").value(expectedPresentationFormatError + ", "
                                                + expectedInputDescriptorFormatError))
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

        @Test
        void testCreateOffer_withOnlyDcqlQuery_thenIllegalArgumentException() throws Exception {

                // Build a minimal DCQL query DTO
                var request = createVerificationManagementWithDcqlQueryDto(null, getDcqlQueryDto(), issuerDids);

                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().is4xxClientError())
                                .andExpect(result -> assertEquals(
                                                IllegalArgumentException.class,
                                                result.getResolvedException().getClass()))
                                .andReturn();
        }

    @Test
    void testCreateOffer_withEmptyAcceptedIssuerDidsAndEmptyTrustAnchors_thenThrowBadRequest()throws Exception {

        var request = CreateVerificationManagementDto.builder()
                .presentationDefinition(presentationDefinitionDto())
                .dcqlQuery(getDcqlQueryDto())
                .trustAnchors(List.of())
                .acceptedIssuerDids(List.of())
                .build();

        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_description").value(
                        containsString("Either acceptedIssuerDids or trustAnchors must be set and cannot be empty.")
                ))
                .andReturn();
    }

    @Test
    void testCreateOffer_withAcceptedIssuerDidsNullValuesAndEmptyTrustAnchors_thenThrowBadRequest()throws Exception {
        final List<String> issuerDids = new ArrayList<>();
        issuerDids.add(null);

        var request = CreateVerificationManagementDto.builder()
                .presentationDefinition(presentationDefinitionDto())
                .dcqlQuery(getDcqlQueryDto())
                .trustAnchors(null)
                .acceptedIssuerDids(issuerDids)
                .build();

        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_description").value(
                        containsString("Either acceptedIssuerDids or trustAnchors must be set and cannot be empty.")
                ))
                .andReturn();
    }

    @Test
    void testCreateOffer_withNullAcceptedIssuerDidsAndNullTrustAnchors_thenThrowBadRequest()throws Exception {

        var request = CreateVerificationManagementDto.builder()
                .presentationDefinition(presentationDefinitionDto())
                .dcqlQuery(getDcqlQueryDto())
                .trustAnchors(null)
                .acceptedIssuerDids(null)
                .build();

        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_description").value(
                        containsString("Either acceptedIssuerDids or trustAnchors must be set and cannot be empty.")
                ))
                .andReturn();
    }

    @Test
    void testCreateOffer_withOnlyTrustAnchors_thenSuccess()throws Exception {
        TrustAnchorDto trustAnchorDto = new TrustAnchorDto("did:example:12345", null);

        var request = CreateVerificationManagementDto.builder()
                .presentationDefinition(presentationDefinitionDto())
                .dcqlQuery(getDcqlQueryDto())
                .trustAnchors(List.of(trustAnchorDto))
                .acceptedIssuerDids(null)
                .build();

        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();
    }

        @Test
        void testCreateOffer_withDcqlQuery_thenSuccess() throws Exception {

                // Build a minimal DCQL query DTO
                var request = createVerificationManagementWithDcqlQueryDto(presentationDefinitionDto(),
                                getDcqlQueryDto(), issuerDids);

                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn();
        }

        @Test
        void testCreateOffer_withComplexDcqlQuery_thenSuccess() throws Exception {
                List<Object> nullContainingList = new ArrayList<>();
                nullContainingList.add("degrees");
                nullContainingList.add(null);
                nullContainingList.add("type");
                var claims = List.of(
                                new DcqlClaimDto(null, nullContainingList, null), // Select all elements of array
                                new DcqlClaimDto(null, List.of("degrees", 1, "title"), null), // Select first element of
                                                                                              // array
                                new DcqlClaimDto(null, List.of("first", "second", "third"), null) // Just selecting
                                                                                                  // something a bit
                                                                                                  // deeper
                );
                var request = createVerificationManagementWithDcqlQueryDto(ApiFixtures.createDcqlQueryDto(claims), issuerDids);
                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isOk());
        }

        @Test
        void testCreateOffer_withComplexDcqlQueryIncludingIllegalValue_thenMethodArgumentNotValid() throws Exception {
                List<Object> nullContainingList = new ArrayList<>();
                nullContainingList.add("degrees");
                nullContainingList.add(null);
                nullContainingList.add("type");
                nullContainingList.add(false); // Booleans not allowed
                var claims = List.of(
                                new DcqlClaimDto(null, nullContainingList, null), // Select all elements of array
                                new DcqlClaimDto(null, List.of("degrees", 1, "title"), null), // Select first element of
                                                                                              // array
                                new DcqlClaimDto(null, List.of("first", "second", "third"), null) // Just selecting
                                                                                                  // something a bit
                                                                                                  // deeper
                );
                var request = createVerificationManagementWithDcqlQueryDto(ApiFixtures.createDcqlQueryDto(claims), issuerDids);
                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().is4xxClientError())
                                .andExpect(result -> assertEquals(
                                                MethodArgumentNotValidException.class,
                                                result.getResolvedException().getClass()))
                                .andReturn();
        }
}