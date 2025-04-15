/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.oid4vp.api.VerificationErrorDto.INVALID_CREDENTIAL;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.StatusListGenerator.createTokenStatusListTokenVerifiableCredential;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.getMultiplePresentationSubmissionString;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.getPresentationSubmissionString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.admin.bj.swiyu.verifier.oid4vp.api.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.oid4vp.service.publickey.DidResolverException;
import ch.admin.bj.swiyu.verifier.oid4vp.service.statuslist.StatusListMaxSizeExceededException;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.ManagementEntityRepository;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.oid4vp.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.oid4vp.service.statuslist.StatusListResolverAdapter;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VerificationControllerIT {

    private static final UUID requestId = UUID.fromString("deadbeef-dead-dead-dead-deaddeafbeef");
    private static final String NONCE_SD_JWT_SQL = "P2vZ8DKAtTuCIU1M7daWLA65Gzoa76tL";
    private static final String PUBLIC_KEY = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"oqBwmYd3RAHs-sFe_U7UFTXbkWmPAaqKTHCvsV8tvxU\",\"y\":\"np4PjpDKNfEDk9qwzZPqjAawiZ8sokVOozHR-Kt89T4\"}";

    @Autowired
    private MockMvc mock;
    @Autowired
    private ManagementEntityRepository managementEntityRepository;
    @Autowired
    private ApplicationProperties applicationProperties;
    @Autowired
    private VerificationProperties verificationProperties;
    @MockitoBean
    private DidResolverAdapter didResolverAdapter;
    @MockitoBean
    private StatusListResolverAdapter mockedStatusListResolverAdapter;

    private UrlRewriteProperties urlRewriteProperties;


    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt_expired.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldFailOnExpiredManagementObject() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isGone());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldFailOnNotAcceptedIssuer() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("suspicious_issuer_id", "suspicious_issuer_id#key-1");
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error_description").value(containsString("Issuer not in list of accepted issuers")));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_mgmt_without_accepted_issuers.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedOnNoAcceptedIssuers() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("some_issuer_id", "some_issuer_id#key-1");
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldGetRequestObject() throws Exception {
        mock.perform(get(String.format("/api/v1/request-object/%s", requestId))
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    var responseJwt = SignedJWT.parse(result.getResponse().getContentAsString());
                    assertThat(responseJwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
                    assertThat(responseJwt.getHeader().getKeyID()).isEqualTo(applicationProperties.getSigningKeyVerificationMethod());
                    assertThat(responseJwt.verify(new ECDSAVerifier(ECKey.parse(PUBLIC_KEY)))).isTrue();

                    // checking claims
                    var claims = responseJwt.getJWTClaimsSet();
                    assertThat(claims.getStringClaim("client_id")).isEqualTo(applicationProperties.getClientId());
                    assertThat(claims.getStringClaim("client_id_scheme")).isEqualTo(applicationProperties.getClientIdScheme());
                    assertThat(claims.getStringClaim("response_type")).isEqualTo("vp_token");
                    assertThat(claims.getStringClaim("response_mode")).isEqualTo("direct_post");
                    assertThat(claims.getStringClaim("nonce")).isNotNull();
                    assertThat(claims.getStringClaim("response_uri")).isEqualTo(String.format("%s/api/v1/request-object/%s/response-data", applicationProperties.getExternalUrl(), requestId));

                    var presentationDefinition = (LinkedTreeMap) claims.getClaim("presentation_definition");
                    assertThat(presentationDefinition.get("id")).isNotNull();
                    assertEquals("Presentation Definition Name", presentationDefinition.get("name"));
                    assertEquals("Presentation Definition Purpose", presentationDefinition.get("purpose"));

                    var inputDescriptors = (List<LinkedTreeMap>) presentationDefinition.get("input_descriptors");
                    var inputDescriptor = inputDescriptors.getFirst();

                    assertThat(inputDescriptor.get("id")).isNotNull();
                    assertEquals("Test Descriptor Name", inputDescriptor.get("name"));
                    assertEquals("Input Descriptor Purpose", inputDescriptor.get("purpose"));

                    var format = (LinkedTreeMap<String, Object>) inputDescriptor.get("format");
                    var vp = (Map<String, List>) format.get("vc+sd-jwt");
                    assertEquals("ES256", vp.get("sd-jwt_alg_values").getFirst());
                    assertEquals("ES256", vp.get("kb-jwt_alg_values").getFirst());

                    var constraints = (LinkedTreeMap<List, List<LinkedTreeMap<List, List>>>) inputDescriptor.get("constraints");
                    assertThat(constraints.get("fields").getFirst().get("path").getFirst()).isEqualTo("$");

                    var clientMetadata = (LinkedTreeMap) claims.getClaim("client_metadata");
                    assertEquals("Fallback name", clientMetadata.get("client_name"));
                    assertEquals("German name (region Switzerland)", clientMetadata.get("client_name#de-CH"));
                    assertEquals("www.example.com/logo.png", clientMetadata.get("logo_uri"));

                    assertThat(result.getResponse().getContentAsString()).doesNotContain("null");
                });
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldAcceptRefusalIWithValidErrorType() throws Exception {
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .formField("error", "vp_formats_not_supported")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isOk());
        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldFailWhenRefusalWithInvalidErrorTypeIs() throws Exception {
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .formField("error", "non_existing_Type")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isBadRequest());
        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.PENDING);

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldRespond404onGetRequestObject() throws Exception {
        UUID notExistingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        mock.perform(get(String.format("/request-object/%s", notExistingRequestId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingSDJWTCredentialFullVC_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2"})
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingSDJWTCredentialWithSD_thenSuccess(String input) throws Exception {
        Integer statusListIndex = "".equals(input) ? null : Integer.parseInt(input);
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT)))
                .thenAnswer(invocation -> createTokenStatusListTokenVerifiableCredential(
                        StatusListGenerator.SPEC_STATUS_LIST,
                        emulator.getKey(),
                        emulator.getIssuerId(),
                        emulator.getKidHeaderValue())
                );

        var sdJWT = emulator.createSDJWTMock(statusListIndex);
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk()).andReturn();

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @ParameterizedTest
    @CsvSource(value = {"0:credential_revoked", "1:credential_suspended", "3:credential_revoked"}, delimiter = ':')
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingSDJWTCredentialWithSD_thenFail(String input, String errorCodeName) throws Exception {
        Integer index = "".equals(input) ? null : Integer.parseInt(input);
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT)))
                .thenAnswer(invocation -> createTokenStatusListTokenVerifiableCredential(
                        StatusListGenerator.SPEC_STATUS_LIST,
                        emulator.getKey(),
                        emulator.getIssuerId(),
                        emulator.getKidHeaderValue())
                );

        var sdJWT = emulator.createSDJWTMock(index);
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response, so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest()).andReturn();

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        var errorCode = managementEntity.getWalletResponse().errorCode().getJsonValue();
        assertThat(errorCode).isEqualTo(errorCodeName);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void twoTimesSameDisclosures_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~" + StringUtils.join(sd, "~") + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");

        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_credential"))
                .andExpect(jsonPath("$.error_description").value("Request contains non-distinct disclosures"));

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt_different_algs.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void wrongAlgorithm_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var response = mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();

        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);

        var responseBody = response.getResponse().getContentAsString();
        assertThat(response.getResponse().getContentAsString())
                .withFailMessage("Should have response body").isNotBlank();
        assertThat(responseBody).contains(INVALID_CREDENTIAL.toString());
        assertThat(responseBody).contains("Invalid algorithm");
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt_different_kb_algs.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void wrongKeyBindingAlgorithm_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        var response = mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();

        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);

        var responseBody = response.getResponse().getContentAsString();
        assertThat(response.getResponse().getContentAsString())
                .withFailMessage("Should have response body").isNotBlank();
        assertThat(responseBody).contains(INVALID_CREDENTIAL.toString());
        assertThat(responseBody).contains("holder_binding_mismatch");
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void notYetValid_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(Instant.now().plus(7, ChronoUnit.DAYS).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT credential is not yet valid"));

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void sdJWTExpired_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");

        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT credential is expired"));

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void sdJWTAdditionalDisclosure_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var additionalDisclosure = new Disclosure("additional", "definetly_wrong");
        var newCred = sdJWT + additionalDisclosure + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");

        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT problem with disclosures and _sd field"));

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt_proof.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldSucceedVerifyingNestedSDJWTCredentialSD_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        vpToken = SDJWTCredentialMock.createMultipleVPTokenMock(vpToken);
        String presentationSubmission = getMultiplePresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldFailVerifyingCredentialOnInvalidStatuslistSignature_thenError() throws Exception {
        Integer statusListIndex = Integer.parseInt("2");
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT))).thenAnswer(invocation -> {
            // holder key is not the one that should have signed the statuslist
            return createTokenStatusListTokenVerifiableCredential(
                    StatusListGenerator.SPEC_STATUS_LIST,
                    emulator.getHolderKey(),
                    emulator.getIssuerId(),
                    emulator.getKidHeaderValue()
            );
        });

        var sdJWT = emulator.createSDJWTMock(statusListIndex);
        var parts = sdJWT.split("~");

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + "~" + StringUtils.join(sd, "~") + "~";
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Failed to verify JWT: Issuer public key does not match signature!"));
    }


    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void shouldVerifyingSDJWTCredentialSDWithDifferentPrivKey_thenException() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Signature mismatch"));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void wrongPresentationSubmission_emptyList_thenException() throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        PresentationSubmissionDto submission = PresentationSubmissionDto.builder()
                .id(UUID.randomUUID().toString())
                .descriptorMap(List.of())
                .build();

        var vpToken = createVpToken();

        String presentationSubmission = mapper.writeValueAsString(submission);

        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("error_description", containsString("DescriptorDto map cannot be empty")));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void wrongPresentationSubmission_emptyObject_thenException() throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        PresentationSubmissionDto submission = PresentationSubmissionDto.builder()
                .id(UUID.randomUUID().toString())
                .descriptorMap(List.of())
                .build();

        var vpToken = createVpToken();

        String presentationSubmission = mapper.writeValueAsString(submission).replace("[]", "[{}]");

        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("error_description", containsString("format - must not be blank")));
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void sendIdxOutOfStatusListBounds_thenException() throws Exception {

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT)))
                .thenAnswer(invocation -> createTokenStatusListTokenVerifiableCredential(
                        StatusListGenerator.SPEC_STATUS_LIST,
                        emulator.getKey(),
                        emulator.getIssuerId(),
                        emulator.getKidHeaderValue())
                );

        var sdJWT = emulator.createSDJWTMock(100);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description", containsString("The VC cannot be validated as the remote list does not contain this VC!")))
                .andReturn();

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void statusListResponseBodyTooBig_thenException() throws Exception {

        // GIVEN
        var expectedErrorMesssage = "Status list size from %s exceeds maximum allowed size".formatted("https://test-statuslist.example");
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        // ContetLengthInterceptor throws invalid argument exception if status list is too big
        when(mockedStatusListResolverAdapter.resolveStatusList(eq(StatusListGenerator.SPEC_SUBJECT)))
                .thenThrow(new StatusListMaxSizeExceededException(expectedErrorMesssage));

        var sdJWT = emulator.createSDJWTMock(100);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description", containsString(expectedErrorMesssage)))
                .andReturn();

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorCode()).isEqualTo(VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST);
    }


    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = "/insert_sdjwt_mgmt.sql")
    @Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "/delete_mgmt.sql")
    void expiredProof_thenException() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost", Instant.now().minusSeconds(verificationProperties.getAcceptableProofTimeWindowSeconds()).getEpochSecond());
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/api/v1/request-object/%s/response-data", requestId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(requestId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    private String createVpToken() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        mockDidResolverResponse(emulator);

        return emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
    }

    private void mockDidResolverResponse(SDJWTCredentialMock sdjwt) throws Exception {
        try {
            when(didResolverAdapter.resolveDid(sdjwt.getIssuerId())).thenAnswer(invocation -> DidDocFixtures.issuerDidDocWithMultikey(
                    sdjwt.getIssuerId(),
                    sdjwt.getKidHeaderValue(),
                    KeyFixtures.issuerPublicKeyAsMultibaseKey()));
        } catch (DidResolverException e) {
            throw new AssertionError(e);
        }

    }
}