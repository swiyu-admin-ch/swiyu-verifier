/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.api.VPApiVersion;
import ch.admin.bj.swiyu.verifier.api.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import com.authlete.sd.Disclosure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.api.VerificationErrorTypeDto.INVALID_CREDENTIAL;
import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.StatusListGenerator.createTokenStatusListTokenVerifiableCredential;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.getMultiplePresentationSubmissionString;
import static ch.admin.bj.swiyu.verifier.oid4vp.test.mock.SDJWTCredentialMock.getPresentationSubmissionString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@ActiveProfiles("test")
class VerificationControllerIT extends BaseVerificationControllerTest {

    private static final String PUBLIC_KEY = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"oqBwmYd3RAHs-sFe_U7UFTXbkWmPAaqKTHCvsV8tvxU\",\"y\":\"np4PjpDKNfEDk9qwzZPqjAawiZ8sokVOozHR-Kt89T4\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String responseDataUriFormat = "/oid4vp/api/request-object/%s/response-data";

    @Autowired
    CacheManager cacheManager;
    @Autowired
    private MockMvc mock;
    @Autowired
    private ManagementRepository managementEntityRepository;
    @Autowired
    private ApplicationProperties applicationProperties;
    @Autowired
    private VerificationProperties verificationProperties;
    @MockitoBean
    private DidResolverAdapter didResolverAdapter;
    @MockitoBean
    private StatusListResolverAdapter mockedStatusListResolverAdapter;

    private static void assertPresentationDefinition(JWTClaimsSet claims) {
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

        var constraints = (LinkedTreeMap<String, List<LinkedTreeMap<String, List>>>) inputDescriptor.get("constraints");
        assertThat(constraints.get("fields").getFirst().get("path").getFirst()).isEqualTo("$");

        var clientMetadata = (LinkedTreeMap) claims.getClaim("client_metadata");
        assertEquals("Fallback name", clientMetadata.get("client_name"));
        assertEquals("German name (region Switzerland)", clientMetadata.get("client_name#de-CH"));
        assertEquals("www.example.com/logo.png", clientMetadata.get("logo_uri"));
    }

    private static void assertDcqlIsComplete(JWTClaimsSet claims) {
        var dcqlQuery = (LinkedTreeMap) claims.getClaim("dcql_query");
        assertThat(dcqlQuery).isNotNull().isNotEmpty();
        var dcqlRequestedCredentials = (List<?>) dcqlQuery.get("credentials");
        assertThat(dcqlRequestedCredentials).isNotNull().isNotEmpty();
        var firstRequestedCredential = (Map<?, ?>) dcqlRequestedCredentials.getFirst();
        assertThat(firstRequestedCredential.get("id")).isNotNull().asString().isNotBlank();
        assertThat(firstRequestedCredential.get("format")).isNotNull().asString().isNotBlank();
        assertThat(firstRequestedCredential.get("meta")).isNotNull();
        assertThat(firstRequestedCredential.get("claims")).isNotNull();

        assertThat(dcqlQuery.get("credential_sets")).isNull();
    }

    @Test
    void shouldFailOnExpiredManagementObject() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_EXPIRED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isGone());
    }

    @Test
    void shouldFailOnNotAcceptedIssuer() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("suspicious_issuer_id", "suspicious_issuer_id#key-1");
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error_description").value(containsString("Issuer not in list of accepted issuers")));
    }

    @Test
    void shouldSucceedOnNoAcceptedIssuers() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("some_issuer_id", "some_issuer_id#key-1");
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_WITHOUT_ACCEPTED_ISSUER))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetRequestObject() throws Exception {
        mock.perform(get(String.format("/oid4vp/api/request-object/%s", REQUEST_ID_SECURED))
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
                    assertThat(claims.getStringClaim("response_uri")).isEqualTo(String.format("%s/oid4vp/api/request-object/%s/response-data", applicationProperties.getExternalUrl(), REQUEST_ID_SECURED));

                    assertDcqlIsComplete(claims);
                    assertPresentationDefinition(claims);

                    assertThat(result.getResponse().getContentAsString()).doesNotContain("null");
                });
    }

    @Test
    void shouldAcceptRefusalIWithValidErrorType() throws Exception {
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .formField("error", "vp_formats_not_supported")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isOk());
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);

    }

    @Test
    void shouldFailWhenRefusalWithInvalidErrorTypeIs() throws Exception {
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .formField("error", "non_existing_Type")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isBadRequest());
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);

    }

    @Test
    void shouldRespond404onGetRequestObject() throws Exception {
        UUID notExistingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        mock.perform(get(String.format("/request-object/%s", notExistingRequestId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSucceedVerifyingSDJWTCredentialFullVC_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2"})
    void shouldSucceedVerifyingSDJWTCredentialWithSD_thenSuccess(String input) throws Exception {
        Integer statusListIndex = "".equals(input) ? null : Integer.parseInt(input);
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT))
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
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk()).andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @ParameterizedTest
    @CsvSource(value = {"0:credential_revoked", "1:credential_suspended", "3:credential_revoked"}, delimiter = ':')
    void shouldSucceedVerifyingSDJWTCredentialWithSD_thenFail(String input, String errorCodeName) throws Exception {
        Integer index = "".equals(input) ? null : Integer.parseInt(input);
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT))
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
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest()).andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        var errorCode = managementEntity.getWalletResponse().errorCode().getJsonValue();
        assertThat(errorCode).isEqualTo(errorCodeName);
    }

    @Test
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
        mock.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_credential"))
                .andExpect(jsonPath("$.error_description").value("Request contains non-distinct disclosures"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void wrongAlgorithm_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var response = mock.perform(post(String.format(responseDataUriFormat, REQUEST_DIFFERENT_ALGS))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_DIFFERENT_ALGS).orElseThrow();

        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);

        var responseBody = response.getResponse().getContentAsString();
        assertThat(response.getResponse().getContentAsString())
                .withFailMessage("Should have response body").isNotBlank();
        assertThat(responseBody.toLowerCase()).contains(INVALID_CREDENTIAL.toString().toLowerCase(), "Invalid algorithm".toLowerCase());
    }

    @Test
    void wrongKeyBindingAlgorithm_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        var response = mock.perform(post(String.format(responseDataUriFormat, REQUEST_DIFFERENT_KB_ALGS))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_DIFFERENT_KB_ALGS).orElseThrow();

        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);

        var responseBody = response.getResponse().getContentAsString();
        assertThat(response.getResponse().getContentAsString())
                .withFailMessage("Should have response body").isNotBlank();
        assertThat(responseBody).contains(INVALID_CREDENTIAL.toString(), "holder_binding_mismatch");
    }

    @Test
    void sdjwtPremature_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(Instant.now().plus(7, ChronoUnit.DAYS).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT credential is not yet valid"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.JWT_PREMATURE, managementEntity.getWalletResponse().errorCode());
    }

    @Test
    void sdJWTExpired_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");

        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT credential is expired"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.JWT_EXPIRED, managementEntity.getWalletResponse().errorCode());
    }

    @Test
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
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT problem with disclosures and _sd field"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, managementEntity.getWalletResponse().errorCode());
    }

    @Test
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
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    void shouldSucceedVerifyingCredentialWithLegacyCNFFormat_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock(true);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        vpToken = SDJWTCredentialMock.createMultipleVPTokenMock(vpToken);
        String presentationSubmission = getMultiplePresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldFailVerifyingCredentialOnInvalidStatuslistSignature_thenError() throws Exception {
        Integer statusListIndex = Integer.parseInt("2");
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT)).thenAnswer(invocation -> {
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
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Failed to verify JWT: Issuer public key does not match signature!"));
    }


    @Test
    void shouldVerifyingSDJWTCredentialSDWithDifferentPrivKey_thenException() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Signature mismatch"));
    }

    @Test
    void wrongPresentationSubmission_emptyList_thenException() throws Exception {

        PresentationSubmissionDto submission = PresentationSubmissionDto.builder()
                .id(UUID.randomUUID().toString())
                .descriptorMap(List.of())
                .build();

        var vpToken = createVpToken();

        String presentationSubmission = objectMapper.writeValueAsString(submission);

        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("error_description", containsString("DescriptorDto map cannot be empty")));
    }

    @Test
    void wrongPresentationSubmission_emptyObject_thenException() throws Exception {

        PresentationSubmissionDto submission = PresentationSubmissionDto.builder()
                .id(UUID.randomUUID().toString())
                .descriptorMap(List.of())
                .build();

        var vpToken = createVpToken();

        String presentationSubmission = objectMapper.writeValueAsString(submission).replace("[]", "[{}]");

        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_request"))
                .andExpect(jsonPath("error_description", containsString("format - must not be blank")));
    }

    @Test
    void sendIdxOutOfStatusListBounds_thenException() throws Exception {

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT))
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
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description", containsString("The VC cannot be validated as the remote list does not contain this VC!")))
                .andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void statusListResponseBodyTooBig_thenException() throws Exception {

        // GIVEN
        var expectedErrorMesssage = "Status list size from %s exceeds maximum allowed size".formatted("https://test-statuslist.example");
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        // ContetLengthInterceptor throws invalid argument exception if status list is too big
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT))
                .thenThrow(new StatusListMaxSizeExceededException(expectedErrorMesssage));

        var sdJWT = emulator.createSDJWTMock(100);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description", containsString(expectedErrorMesssage)))
                .andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorCode()).isEqualTo(VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST);
    }


    @Test
    void expiredProof_thenException() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost", Instant.now().minusSeconds(verificationProperties.getAcceptableProofTimeWindowSeconds()).getEpochSecond(), "kb+jwt");
        String presentationSubmission = getPresentationSubmissionString(UUID.randomUUID());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("presentation_submission", presentationSubmission)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void shouldGetCorrectMandatoryMetadata_thenSuccess() throws Exception {

        mock.perform(get("/oid4vp/api/openid-client-metadata.json")
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value(applicationProperties.getClientId()))
                .andExpect(jsonPath("$.vp_formats.jwt_vp.alg").value(JWSAlgorithm.ES256.getName()))
                .andExpect(jsonPath("$.version").value(applicationProperties.getMetadataVersion()));
    }

    private String createVpToken() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        mockDidResolverResponse(emulator);

        return emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "http://localhost");
    }

    private void mockDidResolverResponse(SDJWTCredentialMock sdjwt) {
        try {
            when(didResolverAdapter.resolveDid(sdjwt.getIssuerId())).thenAnswer(invocation -> DidDocFixtures.issuerDidDocWithMultikey(
                    sdjwt.getIssuerId(),
                    sdjwt.getKidHeaderValue(),
                    KeyFixtures.issuerPublicKeyAsMultibaseKey()));
        } catch (DidResolverException e) {
            throw new AssertionError(e);
        }

    }

    @Test
    void shouldSucceedForDCQLEndpoint() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "http://localhost");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
        assertThat(managementEntity.getWalletResponse().credentialSubjectData())
                .contains("first_name")
                .contains("TestFirstname")
                .contains("last_name")
                .contains("TestLastName");
    }


    @Test
    void shouldBadRequestForDCQLEndpoint_whenMalformedVpToken_notConsumedPresentationRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "http://localhost");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, sdJwt);
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    @Test
    void shouldBadRequestForDCQLEndpoint_whenAlteredSdJwt() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "http://localhost");
        // Split jwt, disclosures & binding proof
        var parts = sdJwt.split("~");
        sdJwt = parts[0] + "~" + parts[1] + "~" + parts[parts.length - 1];
        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").value("invalid_transaction_data"))
                .andExpect(jsonPath("$.error_code").value("holder_binding_mismatch"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().credentialSubjectData()).isNull();
    }

    @Test
    void shouldBadRequestForDCQLEndpoint_whenMissingClaim() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        // Split jwt, disclosures
        var parts = unsignedSdJwt.split("~");
        // Only have the first claim (first_name) as disclosure
        unsignedSdJwt = parts[0] + "~" + parts[1] + "~";
        // Sign the presentation
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "http://localhost");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").value("invalid_transaction_data"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().credentialSubjectData()).isNull();
    }

    @Test
    void shouldThrowUnsupportedOperationExceptionForDCQLEncryptedEndpoint() throws Exception {
        // GIVEN
        // Create encrypted DCQL response string
        String encryptedResponse = "eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMjU2R0NNIiwidHlwIjoiSldFIn0...";

        // WHEN / THEN
        mock.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("response", encryptedResponse))
                .andExpect(status().is4xxClientError());

        // Verify that the management entity remains in pending state since the exception is thrown early
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    @Test
    void shouldHandleClientRejectionThroughRejectionEndpoint() throws Exception {
        // GIVEN
        String errorDescription = "User declined the verification request";

        // WHEN / THEN
        mock.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("error", "access_denied")
                        .formField("error_description", errorDescription))
                .andExpect(status().isOk());

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorDescription()).isEqualTo(errorDescription);
    }

    @Test
    void shouldHandleClientRejectionWithOnlyError() throws Exception {
        // WHEN / THEN
        mock.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("error", "client_rejected"))
                .andExpect(status().isOk());

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void shouldHandleClientRejectionWithEmptyErrorDescription() throws Exception {
        // WHEN / THEN
        mock.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("error", "vp_formats_not_supported")
                        .formField("error_description", ""))
                .andExpect(status().isOk());

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorDescription()).isEmpty();
    }

    @Test
    void shouldFailClientRejectionOnExpiredRequest() throws Exception {
        // WHEN / THEN
        mock.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_EXPIRED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("error", "access_denied")
                        .formField("error_description", "User cancelled"))
                .andExpect(status().isGone());

        // Verify that the management entity state remains unchanged
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_EXPIRED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    @Test
    void shouldFailClientRejectionWithInvalidErrorType() throws Exception {
        // WHEN / THEN
        mock.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("error", "invalid_error_type")
                        .formField("error_description", "Some description"))
                .andExpect(status().isBadRequest());

        // Verify that the management entity remains in pending state
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }
}
