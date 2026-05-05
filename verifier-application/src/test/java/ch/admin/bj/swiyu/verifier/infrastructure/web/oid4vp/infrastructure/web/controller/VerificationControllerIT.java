package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.domain.management.*;
import ch.admin.bj.swiyu.verifier.dto.VPApiVersion;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDObjectBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static ch.admin.bj.swiyu.verifier.dto.VerificationErrorTypeDto.INVALID_CREDENTIAL;
import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator.createTokenStatusListTokenVerifiableCredential;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.*;
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

    /**
     * List of IDS to be used in Parameterized tests with different verification requests
     */
    private static final List<UUID> DEFAULT_REQUEST_OBJECT_SOURCE = List.of(REQUEST_ID_SECURED, REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED);

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
    @Autowired
    private DataSource dataSource;
    @MockitoBean
    private DidResolverFacade didResolverFacade;
    @MockitoBean
    private StatusListResolverAdapter mockedStatusListResolverAdapter;

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
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_EXPIRED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant - should be made redundant by IssuerTrustValidator")
    void shouldFailOnNotAcceptedIssuer() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("suspicious_issuer_id", "suspicious_issuer_id#key-1");
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error_description").value(containsString("Issuer not in list of accepted issuers")));
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant - should be made redundant by IssuerTrustValidator")
    void shouldSucceedOnNoAcceptedIssuers() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock("some_issuer_id", "some_issuer_id#key-1");
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        sendPresentation(REQUEST_ID_WITHOUT_ACCEPTED_ISSUER, vpToken);
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

    @ParameterizedTest
    @FieldSource("DEFAULT_REQUEST_OBJECT_SOURCE")
    @Disabled("Check in EIDOMNI-926 if test is redundant - Potentially made redundant by BlackboxIt?")
    void shouldSucceedVerifyingSDJWTCredentialFullVC_thenSuccess(UUID requestObjectId) throws Exception {
        assertThat(requestObjectId).isIn(DEFAULT_REQUEST_OBJECT_SOURCE); // Nonsense Assert to stop linters going insane about unused field
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);


        // WHEN / THEN
        sendPresentation(requestObjectId, vpToken);

        var managementEntity = managementEntityRepository.findById(requestObjectId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    private void sendPresentation(UUID requestObjectId, String vpToken) throws Exception {
        var managementEntity = managementEntityRepository.findById(requestObjectId).orElseThrow();
        ResponseSpecification responseSpecification = managementEntity.getResponseSpecification();
        if (responseSpecification.getResponseModeType() == ResponseModeType.DIRECT_POST) {
            mock.perform(post(String.format(responseDataUriFormat, requestObjectId))
                            .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                            .formField("vp_token", vpToken))
                    .andExpect(status().isOk());
        } else if (responseSpecification.getResponseModeType() == ResponseModeType.DIRECT_POST_JWT) {
            // JWKS & encryptionMethod are normally provided in Request Object
            ECKey publicKey = JWKSet.parse(responseSpecification.getJwks()).getKeys().getFirst().toECKey();
            var encryptionMethod = EncryptionMethod.parse(responseSpecification.getEncryptedResponseEncValuesSupported().getFirst());

            JWEObject jweObject = new JWEObject(
                    new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, encryptionMethod)
                            .keyID(publicKey.getKeyID()).build(),
                    new JWTClaimsSet.Builder()
                            .claim("vp_token", vpToken).build().toPayload()
            );
            jweObject.encrypt(new ECDHEncrypter(publicKey));
            mock.perform(post(String.format(responseDataUriFormat, requestObjectId))
                            .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                            .formField("response", jweObject.serialize()))
                    .andExpect(status().isOk());
        }
    }

    private HikariPoolMXBean hikariPool() {
        return ((HikariDataSource) dataSource).getHikariPoolMXBean();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2"})
    @Disabled("Check in EIDOMNI-926 if test is redundant - Potentially made redundant by BlackboxIt?")
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
        var parts = sdJWT.split(SdJwt.JWT_PART_DELINEATION_CHARACTER);

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + SdJwt.JWT_PART_DELINEATION_CHARACTER + StringUtils.join(sd, SdJwt.JWT_PART_DELINEATION_CHARACTER) + SdJwt.JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isOk()).andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @ParameterizedTest
    @CsvSource(value = {"0:credential_revoked", "1:credential_suspended", "3:credential_revoked"}, delimiter = ':')
    @Disabled("Check in EIDOMNI-926 if test is redundant - Wrong Format?")
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
        var parts = sdJWT.split(SdJwt.JWT_PART_DELINEATION_CHARACTER);

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + SdJwt.JWT_PART_DELINEATION_CHARACTER + StringUtils.join(sd, SdJwt.JWT_PART_DELINEATION_CHARACTER) + SdJwt.JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response, so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest()).andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        var errorCode = managementEntity.getWalletResponse().errorCode().getJsonValue();
        assertThat(errorCode).isEqualTo(errorCodeName);
    }

    @Test
    void shouldRejectDCQLPresentation_whenCredentialIsRevoked_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        // idx=0 has status value 1 (revoked) in SPEC_STATUS_LIST
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT))
                .thenAnswer(invocation -> createTokenStatusListTokenVerifiableCredential(
                        StatusListGenerator.SPEC_STATUS_LIST,
                        emulator.getKey(),
                        emulator.getIssuerId(),
                        emulator.getKidHeaderValue())
                );

        var sdJwt = emulator.createSDJWTMock(0);
        var boundSdJwt = emulator.addKeyBindingProof(sdJwt, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var vpTokenMap = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(boundSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpTokenMap);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("credential_revoked"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorCode().getJsonValue()).isEqualTo("credential_revoked");
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant - Wrong Format?")
    void twoTimesSameDisclosures_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var parts = sdJWT.split(SdJwt.JWT_PART_DELINEATION_CHARACTER);

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + SdJwt.JWT_PART_DELINEATION_CHARACTER
                + StringUtils.join(sd, SdJwt.JWT_PART_DELINEATION_CHARACTER) + SdJwt.JWT_PART_DELINEATION_CHARACTER
                + StringUtils.join(sd, SdJwt.JWT_PART_DELINEATION_CHARACTER) + SdJwt.JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_credential"))
                .andExpect(jsonPath("$.error_description").value("Request contains non-distinct disclosures"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }


    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant - Wrong Format?")
    void wrongKeyBindingAlgorithm_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");
        mockDidResolverResponse(emulator);

        var response = mock.perform(post(String.format(responseDataUriFormat, REQUEST_DIFFERENT_KB_ALGS))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
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
    @Disabled("Check in EIDOMNI-926 if test is redundant")
    void sdjwtPremature_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(Instant.now().plus(7, ChronoUnit.DAYS).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT credential is not yet valid"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.JWT_PREMATURE, managementEntity.getWalletResponse().errorCode());
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant")
    void sdJWTExpired_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");


        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT credential is expired"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.JWT_EXPIRED, managementEntity.getWalletResponse().errorCode());
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant")
    void sdJWTAdditionalDisclosure_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var additionalDisclosure = new Disclosure("additional", "definetly_wrong");
        var newCred = sdJWT + additionalDisclosure + SdJwt.JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "did:example:12345");


        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Could not verify JWT problem with disclosures and _sd field"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, managementEntity.getWalletResponse().errorCode());
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant")
    void shouldSucceedVerifyingNestedSDJWTCredentialSD_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");
        vpToken = SDJWTCredentialMock.createMultipleVPTokenMock(vpToken);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        sendPresentation(REQUEST_ID_SECURED, vpToken);

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vc+sd-jwt", "dc+sd-jwt"})
    @Disabled("Check in EIDOMNI-926 if test is redundant")
    void shouldSucceedVerifyingCredentialWithLegacyCNFFormat_thenSuccess(String credentialFormat) throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock(true, credentialFormat);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");
        vpToken = SDJWTCredentialMock.createMultipleVPTokenMock(vpToken);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        sendPresentation(REQUEST_ID_SECURED, vpToken);
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant")
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
        var parts = sdJWT.split(SdJwt.JWT_PART_DELINEATION_CHARACTER);

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + SdJwt.JWT_PART_DELINEATION_CHARACTER + StringUtils.join(sd, SdJwt.JWT_PART_DELINEATION_CHARACTER) + SdJwt.JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Failed to verify JWT: Issuer public key does not match signature!"));
    }


    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant or could be made into a regular test")
    void shouldVerifyingSDJWTCredentialSDWithDifferentPrivKey_thenException() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description").value("Signature mismatch"));
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant or could be made into a regular test")
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
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .formField("vp_token", vpToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_credential"))
                .andExpect(jsonPath("error_description", containsString("The VC cannot be validated as the remote list does not contain this VC!")))
                .andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant or could be made into a regular test")
    void statusListResponseBodyTooBig_thenException() throws Exception {

        // GIVEN
        var expectedErrorMesssage = "Status list size from %s exceeds maximum allowed size".formatted("https://test-statuslist.example");
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        // ContetLengthInterceptor throws invalid argument exception if status list is too big
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT))
                .thenThrow(new StatusListMaxSizeExceededException(expectedErrorMesssage));

        var sdJWT = emulator.createSDJWTMock(100);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
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
    @Disabled("Check in EIDOMNI-926 if test is redundant or could be made into a regular test")
    void expiredProof_thenException() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345", Instant.now().minusSeconds(verificationProperties.getAcceptableProofTimeWindowSeconds()).getEpochSecond(), "kb+jwt");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
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
                .andExpect(jsonPath("$.vp_formats.jwt_vp.alg").value(JWSAlgorithm.ES256.getName()));
    }

    private String createVpToken() throws Exception {
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        mockDidResolverResponse(emulator);

        return emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "did:example:12345");
    }

    private void mockDidResolverResponse(SDJWTCredentialMock sdjwt) {
        try {
            String issuerKeyId = sdjwt.getIssuerId() + "#key-1";
            String fragment = "key-1";
            when(didResolverFacade.resolveDid(sdjwt.getIssuerId(), fragment))
                    .thenAnswer(invocation -> DidDocFixtures.issuerDidDocWithJsonWebKey(
                            sdjwt.getIssuerId(), issuerKeyId, KeyFixtures.issuerPublicKeyAsJsonWebKey()).getKey(fragment));
        } catch (DidResolverException | ch.admin.eid.did_sidekicks.DidSidekicksException e) {
            throw new AssertionError(e);
        }

    }

    @Test
    void testDCQLEndpoint_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var parts = unsignedSdJwt.split(SdJwt.JWT_PART_DELINEATION_CHARACTER);
        var disclosures = Arrays.copyOfRange(parts, 1, parts.length);
        var discList = new java.util.ArrayList<>(Arrays.asList(disclosures));
            // remove index 2 first, then index 1 to keep indices stable
            discList.remove(2);
            discList.remove(1);
        var rebuiltSdJwt = parts[0]
                + SdJwt.JWT_PART_DELINEATION_CHARACTER
                + StringUtils.join(discList, SdJwt.JWT_PART_DELINEATION_CHARACTER)
                + SdJwt.JWT_PART_DELINEATION_CHARACTER;

        var sdJwt = emulator.addKeyBindingProof(rebuiltSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientId());
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));

        // remove unused list disclosures
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
                .contains("TestLastName")
                .contains("languages");
    }

    @Test
    void testDCQLEndpoint_withKeybinding_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientId());

        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    void testDCQLNestedEndpoint_withKeybinding_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createNestedSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientId());

        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_NESTED_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_NESTED_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    void testDCQLNestedEndpoint_forArray_thenSuccess() throws Exception {

        var dcqlCredentialId = UUID.randomUUID();

        var dcqlQuery = """
                {
                "credentials": [
                    {
                      "id": "%s",
                      "format": "dc+sd-jwt",
                      "meta": {
                        "vct_values": [ "%s" ]
                      },
                      "require_cryptographic_holder_binding": true,
                      "claims": [
                          {"path": ["languages", 2], "values": ["IT"]}
                      ]
                    }
                  ]
                }
                """.formatted(dcqlCredentialId, SDJWTCredentialMock.DEFAULT_VCT);

        managementEntityRepository.save(Management.builder()
                .id(dcqlCredentialId)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .dcqlQuery(dcqlQuery(dcqlQuery))
                .acceptedIssuerDids(List.of("TEST_ISSUER_ID"))
                .build());

        // GIVEN
        List<Disclosure> disclosures = new ArrayList<>();
        SDObjectBuilder builder = new SDObjectBuilder();
        var languages = Stream.of("DE", "FR", "IT").map(lang -> {
            var languageDisclosure = new Disclosure(lang);
            disclosures.add(languageDisclosure);
            return languageDisclosure.toArrayElement();
        }).toList();

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var languagesDisclosure = new Disclosure("languages", languages);
        disclosures.add(languagesDisclosure);
        builder.putSDClaim(languagesDisclosure);

        var used = disclosures.stream().filter(disc -> (Objects.equals(disc.getClaimName(), "languages") || disc.getClaimValue().equals("IT"))).toList();
        var sdjwtWithoutKeyBinding = emulator.createSdJWT(builder, disclosures, null, null, null, DEFAULT_VCT, false, "vc+sd-jwt", JWSAlgorithm.ES256, false);
        var test = sdjwtWithoutKeyBinding.split("~")[0]
                .concat(used.stream().map(disc -> "~" + disc.toString()).reduce("", String::concat))
                .concat("~");

        var sdJwt = emulator.addKeyBindingProof(test, NONCE_SD_JWT_SQL, applicationProperties.getClientId());

        mockDidResolverResponse(emulator);
        var vpToken = Map.of(dcqlCredentialId, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, dcqlCredentialId))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isOk());
    }

    @Test
    void testDCQLEndpoint_withKeybindingButNotRequested_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientId());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    void testDCQLEndpoint_withoutKeybinding_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock(true);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(unsignedSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    void testDCQLEndpoint_missingHolderBinding_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(unsignedSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void testDCQLEndpoint_invalidHolderBinding_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "incorrect-audience");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void testDCQLEndpoint_holderBindingRequestedButNotPossible_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock(true);

        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(unsignedSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }


    @Test
    void shouldBadRequestForDCQLEndpoint_whenWrongAudience() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "http://localhost:8080");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        mock.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("vp_token", submissionData))
                .andExpect(jsonPath("$.error_description").value("Holder Binding audience mismatch. Actual: 'http://localhost:8080'. Expected: did:example:12345"))
                .andExpect(jsonPath("$.error_code").value("holder_binding_mismatch"))
                .andExpect(status().isBadRequest());
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
        var parts = sdJwt.split(SdJwt.JWT_PART_DELINEATION_CHARACTER);
        sdJwt = parts[0] + SdJwt.JWT_PART_DELINEATION_CHARACTER + parts[1] + SdJwt.JWT_PART_DELINEATION_CHARACTER + parts[parts.length - 1];
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
        var parts = unsignedSdJwt.split(SdJwt.JWT_PART_DELINEATION_CHARACTER);
        // Only have the first claim (first_name) as disclosure
        unsignedSdJwt = parts[0] + SdJwt.JWT_PART_DELINEATION_CHARACTER + parts[1] + SdJwt.JWT_PART_DELINEATION_CHARACTER;
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

    @Test
    void shouldGetRequestObjectEncryptionRequired() throws Exception {
        mock.perform(get(String.format("/oid4vp/api/request-object/%s", REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED))
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
                    assertThat(claims.getStringClaim("response_mode")).isEqualTo("direct_post.jwt");
                    assertThat(claims.getStringClaim("nonce")).isNotNull();
                    assertThat(claims.getStringClaim("response_uri")).isEqualTo(String.format("%s/oid4vp/api/request-object/%s/response-data", applicationProperties.getExternalUrl(), REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED));

                    assertDcqlIsComplete(claims);

                    assertThat(result.getResponse().getContentAsString()).doesNotContain("null");
                    var metadata = objectMapper.readValue(objectMapper.writeValueAsString(claims.getClaim("client_metadata")), OpenidClientMetadataDto.class);
                    assertThat(metadata.getJwks()).isNotNull();
                    assertThat(metadata.getJwks().keys()).isNotEmpty();
                    assertThat(metadata.getEncryptedResponseEncValuesSupported()).isNotEmpty();
                    assertThat(metadata.getEncryptedResponseEncValuesSupported()).contains("A128GCM");
                    var encryptionKeys = JWKSet.parse(objectMapper.writeValueAsString(metadata.getJwks()));
                    assertThat(encryptionKeys.containsNonPublicKeys()).isFalse();
                    assertThat(encryptionKeys.getKeys()).isNotEmpty();
                });

    }

    @Test
    @Disabled("Check in EIDOMNI-926 if test is redundant or fix using dcql")
    void shouldHandleConcurrentVerificationRequests_whenExternalDependencyBlocks() throws Exception {

        final SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        final String sdJwt = emulator.createSDJWTMock();
        String vpToken = emulator.addKeyBindingProof(sdJwt, NONCE_SD_JWT_SQL, "did:example:12345");
        vpToken = SDJWTCredentialMock.createMultipleVPTokenMock(vpToken);


        final CountDownLatch didCallStarted = new CountDownLatch(1);

        // Simulate did resolution blocking
        when(didResolverFacade.resolveDid(emulator.getIssuerId(), "key-1"))
                .thenAnswer(invocation -> {
                    didCallStarted.countDown();
                    Thread.sleep(Long.MAX_VALUE);
                    return DidDocFixtures.issuerDidDocWithJsonWebKey(
                            emulator.getIssuerId(),
                            emulator.getIssuerId() + "#key-1",
                            KeyFixtures.issuerPublicKeyAsJsonWebKey()).getKey("key-1");
                });

        final HikariPoolMXBean pool = hikariPool();

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final String finalVpToken = vpToken;
        executor.submit(() -> {
            try {
                mock.perform(
                        post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .formField("vp_token", finalVpToken)
                );
            } catch (Exception ignored) {}
        });

        assertThat(didCallStarted.await(5, TimeUnit.SECONDS)).isTrue();

        final int activeConnections = pool.getActiveConnections();

        assertThat(activeConnections)
                .as("No JDBC connection must be leaked even if external call never returns")
                .isZero();

        executor.shutdownNow();
    }

    @Test
    void shouldNotDeadlockVerificationFlow_whenExternalDependencyBlocks() throws Exception {
        final int concurrentRequests = 5;
        final SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        final String sdJwt = emulator.createSDJWTMock();
        String vpToken = emulator.addKeyBindingProof(sdJwt, NONCE_SD_JWT_SQL, "did:example:12345");
        vpToken = SDJWTCredentialMock.createMultipleVPTokenMock(vpToken);


        final CountDownLatch didCallStarted = new CountDownLatch(concurrentRequests);
        final CountDownLatch allowDidToFinish = new CountDownLatch(concurrentRequests);

        // Simulate did resolution blocking
        when(didResolverFacade.resolveDid(emulator.getIssuerId(), "key-1"))
                .thenAnswer(invocation -> {
                    didCallStarted.countDown();
                    try {
                        allowDidToFinish.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return DidDocFixtures.issuerDidDocWithJsonWebKey(
                            emulator.getIssuerId(),
                            emulator.getIssuerId() + "#key-1",
                            KeyFixtures.issuerPublicKeyAsJsonWebKey()).getKey("key-1");
                });

        final HikariPoolMXBean pool = hikariPool();

        final ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < concurrentRequests; i++) {
            final String finalVpToken = vpToken;
            executor.submit(() -> {
                try {
                    mock.perform(
                            post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                    .formField("vp_token", finalVpToken)
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        final int activeConnections = pool.getActiveConnections();

        assertThat(activeConnections)
                .as("Concurrent blocked requests must not exhaust JDBC pool")
                .isZero();

        allowDidToFinish.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}