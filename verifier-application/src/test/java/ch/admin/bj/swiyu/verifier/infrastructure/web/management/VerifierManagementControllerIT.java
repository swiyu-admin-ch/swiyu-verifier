package ch.admin.bj.swiyu.verifier.infrastructure.web.management;

import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.TrustAnchorDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlClaimDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.service.management.fixtures.ApiFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import static ch.admin.bj.swiyu.verifier.service.management.fixtures.ApiFixtures.*;
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

    private List<String> issuerDids = List.of(UUID.randomUUID().toString());


    @Test
    void testCreateOffer_withEmptyAcceptedIssuerDidsAndEmptyTrustAnchors_thenThrowBadRequest()throws Exception {

        var request = CreateVerificationManagementDto.builder()
                .dcqlQuery(getDcqlQueryForListDto())
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
                .dcqlQuery(getDcqlQueryForListDto())
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
                .dcqlQuery(getDcqlQueryForListDto())
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
                .dcqlQuery(getDcqlQueryForListDto())
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
                var request = createVerificationManagementWithDcqlQueryDto(getDcqlQueryForListDto(), issuerDids);

                mvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new ObjectMapper().writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn();
        }

        @Test
        void testCreateOffer_withDcqlQueryWithoutResponseMode_thenSuccess() throws Exception {

                // Build a minimal DCQL query DTO
                var request = createVerificationManagementWithoutResponseMode(issuerDids, getDcqlQueryForListDto());

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