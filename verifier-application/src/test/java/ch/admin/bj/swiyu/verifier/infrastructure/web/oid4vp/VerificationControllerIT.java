package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.sdjwtutil.SdJwtConstants;
import ch.admin.bj.swiyu.verifier.common.DcqlTestHelper;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.TrustMethod;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.dto.VPApiVersion;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolver;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDObjectBuilder;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.shaded.gson.internal.LinkedTreeMap;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
import static ch.admin.bj.swiyu.verifier.dto.VerificationErrorTypeDto.INVALID_CREDENTIAL;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator.createTokenStatusListTokenVerifiableCredential;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_VCT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class VerificationControllerIT extends BaseVerificationControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String responseDataUriFormat = "/oid4vp/api/request-object/%s/response-data";

    /**
     * List of IDS to be used in Parameterized tests with different verification requests
     */
    private static final List<UUID> DEFAULT_REQUEST_OBJECT_SOURCE = List.of(REQUEST_ID_SECURED, REQUEST_ID_SDJWT_RESPONSE_ENCRYPTED);

    @Autowired
    private ManagementRepository managementEntityRepository;

    @Autowired
    private VerificationProperties verificationProperties;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private StatusListResolver mockedStatusListResolverAdapter;

    private final String clientId = "did:example:12345";
    private final String prefix = "decentralized_identifier";
    private final String clientIdWithPrefix = prefix + ":" + clientId;

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
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_EXPIRED, vpToken, REQUEST_ID_EXPIRED)
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailOnNotAcceptedIssuer() throws Exception {
        var ATTACKER_DID = "did:webvh:some-scid:example.com:api:v1:suspicious-issuer-id";
        var ATTACKER_KID = ATTACKER_DID + "#" + "key-1";
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(ATTACKER_DID, ATTACKER_KID);
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error_description").value(containsString("Issuer not in list of accepted issuers")));
    }

    @Test
    @Disabled("Behavior changed: IssuerTrustValidator now requires explicit trust configuration. " +
            "When both acceptedIssuerDids AND trustAnchors are empty, all credentials are rejected. " +
            "This test assumed empty list = any issuer allowed, which is no longer the case.")
    void shouldSucceedOnNoAcceptedIssuers() throws Exception {
        var ARBITRARY_DID = "did:webvh:some-scid:example.com:api:v1:some-issuer-id";
        var ARBITRARY_KID = ARBITRARY_DID + "#" + "key-1";
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(ARBITRARY_DID, ARBITRARY_KID);
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", REQUEST_ID_WITHOUT_ACCEPTED_ISSUER));
        sendVerificationResponse(String.format(responseDataUriFormat, REQUEST_ID_WITHOUT_ACCEPTED_ISSUER), vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri").doesNotExist()).andReturn();
    }

    @Test
    void shouldGetRequestObject() throws Exception {
        var expectedClientIdWithPrefix = applicationProperties.getClientIdPrefix() + ":" + applicationProperties.getClientId();
        mockMvc.perform(get(String.format("/oid4vp/api/request-object/%s", REQUEST_ID_SECURED))
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    var responseJwt = SignedJWT.parse(result.getResponse().getContentAsString());
                    assertThat(responseJwt.getHeader().getAlgorithm().getName()).isEqualTo("ES256");
                    assertThat(responseJwt.getHeader().getKeyID()).isEqualTo(applicationProperties.getSigningKeyVerificationMethod());
                    assertThat(responseJwt.verify(new ECDSAVerifier(ECKey.parse(PUBLIC_KEY)))).isTrue();

                    // checking claims
                    var claims = responseJwt.getJWTClaimsSet();
                    assertThat(claims.getStringClaim("client_id")).isEqualTo(expectedClientIdWithPrefix);
                    assertThat(claims.getStringClaim("response_type")).isEqualTo("vp_token");
                    assertThat(claims.getStringClaim("response_mode")).isEqualTo("direct_post");
                    assertThat(claims.getStringClaim("nonce")).isNotNull();
                    assertThat(claims.getStringClaim("response_uri")).isEqualTo(String.format("%s/oid4vp/api/request-object/%s/response-data", applicationProperties.getExternalUrl(), REQUEST_ID_SECURED));

                    assertDcqlIsComplete(claims);

                    assertThat(result.getResponse().getContentAsString()).doesNotContain("null");
                });
    }

    @Test
    void shouldGetRequestObject_withoutClientIdPrefix() throws Exception {
        when(applicationProperties.getClientIdPrefix()).thenReturn(null);
        mockMvc.perform(get(String.format("/oid4vp/api/request-object/%s", REQUEST_ID_SECURED))
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andDo(result -> {
                    var responseJwt = SignedJWT.parse(result.getResponse().getContentAsString());
                    var claims = responseJwt.getJWTClaimsSet();
                    assertThat(claims.getStringClaim("client_id")).isEqualTo(applicationProperties.getClientId());
                });
    }

    @Test
    void shouldAcceptRefusalIWithValidErrorType() throws Exception {
        mockMvc.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .formField("state", REQUEST_ID_SECURED.toString())
                        .formField("error", "vp_formats_not_supported")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isOk());
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void shouldFailWhenRefusalWithInvalidErrorTypeIs() throws Exception {
        mockMvc.perform(post(String.format(responseDataUriFormat, REQUEST_ID_SECURED))
                        .formField("error", "non_existing_Type")
                        .formField("error_description", "I really just dont want to"))
                .andExpect(status().isBadRequest());
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);

    }

    @Test
    void shouldRespond404onGetRequestObject() throws Exception {
        UUID notExistingRequestId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        mockMvc.perform(get(String.format("/request-object/%s", notExistingRequestId)))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @FieldSource("DEFAULT_REQUEST_OBJECT_SOURCE")
    void shouldSucceedVerifyingSDJWTCredentialFullVC_thenSuccess(UUID requestObjectId) throws Exception {
        assertThat(requestObjectId).isIn(DEFAULT_REQUEST_OBJECT_SOURCE); // Nonsense Assert to stop linters going insane about unused field
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", requestObjectId));

        sendVerificationResponse(String.format(responseDataUriFormat, requestObjectId), vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri").doesNotExist()).andReturn();

        var managementEntity = managementEntityRepository.findById(requestObjectId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
        var managementVpTokens = managementEntity.getWalletResponse().vpToken();
        assertThat(managementVpTokens).as("Only a single Credential entry in dcql query was requested").hasSize(1);
        var presentedVpTokens = managementVpTokens.values().stream().findFirst().get();
        assertThat(presentedVpTokens).as("Only a sinlge VC was presented").hasSize(1)
            .as("The vp token saved should be the one presented")
            .contains(vpToken);
        var evaluations = managementEntity.getCredentialEvaluation();
        assertThat(evaluations).as("In DCQL query only has a single credential").hasSize(1);
        var presentationEvaluation = evaluations.values().stream().findFirst().get();
        assertThat(presentationEvaluation).as("Only a single VC was presented").hasSize(1);
        var credentialStatus = presentationEvaluation.getFirst().credentialStatus();
        assertThat(credentialStatus).as("VC had no status list reference and no credential status was validated").isNull();
        var trust = presentationEvaluation.getFirst().trustMarkers();
        assertThat(trust.trustMethod()).as("Issuer was in list of trusted issuers").isEqualTo(TrustMethod.TRUSTED_AUTHORITY);
        assertThat(trust.isTrusted()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("dcqlQueryProvider")
    void shouldSucceedVerifyingSDJWTCredentialFullVCWithNested_thenSuccess(
            int expectedIndex,
            String expectedCountry) throws Exception {

        var createResponseDto = getAddressArrayManagement(expectedIndex, expectedCountry);

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSimpleNestedSDJWTMock();

        List<String> list = new ArrayList<>(Arrays.asList(sdJWT.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER)));

        var fixedSdjwt = String.join(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER, list) + "~";
        var vpToken = emulator.addKeyBindingProof(fixedSdjwt, createResponseDto.requestNonce(), clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", createResponseDto.id()));

        sendVerificationResponse(String.format(responseDataUriFormat, createResponseDto.id()), vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri").doesNotExist()).andReturn();

        var mgmtResponse = getManagementObjectById(createResponseDto.id().toString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode walletResponse = objectMapper.readTree(mgmtResponse).get("wallet_response");
        var addresses = walletResponse.get("credential_subject_data").get("defaultTestDcqlCredentialId").asArray().get(0).get("addresses").asArray();

        // as all addresses are sent in disclosures, all addresses should be present in the verification result, even if some were not requested
        assertThat(addresses).hasSize(2);

        assertThat(addresses.get(expectedIndex)).isNotNull();
        assertThat(addresses.get(expectedIndex).get("country").asString()).isEqualTo(expectedCountry);
    }

    @ParameterizedTest
    @MethodSource("dcqlQueryProvider")
    void shouldSucceedVerifyingSDJWTCredentialFullVCWithNestedOnlyWithNecessaryDisclosures_thenSuccess(
            int expectedIndex,
            String expectedCountry,
            int disclosureIndexStart,
            int disclosureIndexEnd) throws Exception {

        var createResponseDto = getAddressArrayManagement(expectedIndex, expectedCountry);

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSimpleNestedSDJWTMock();

        // remove fist 2 disclosures to simulate a holder that only discloses the second address
        List<String> list = new ArrayList<>(Arrays.asList(sdJWT.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER)));
        list.subList(disclosureIndexStart, disclosureIndexEnd).clear();

        var fixedSdjwt = String.join(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER, list) + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(fixedSdjwt, createResponseDto.requestNonce(), clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", createResponseDto.id()));

        sendVerificationResponse(String.format(responseDataUriFormat, createResponseDto.id()), vpToken, requestObject)
                .andExpect(status().isOk());

        var mgmtResponse = getManagementObjectById(createResponseDto.id().toString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode walletResponse = objectMapper.readTree(mgmtResponse).get("wallet_response");
        var addresses = walletResponse.get("credential_subject_data").get("defaultTestDcqlCredentialId").asArray().get(0).get("addresses").asArray();

        // as all addresses are sent in disclosures, all addresses should be present in the verification result, even if some were not requested
        assertThat(addresses).hasSize(2);

        assertThat(addresses.get(expectedIndex)).isNotNull();
        assertThat(addresses.get(expectedIndex).get("country").asString()).isEqualTo(expectedCountry);
    }

    private HikariPoolMXBean hikariPool() {
        return ((HikariDataSource) dataSource).getHikariPoolMXBean();
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
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isOk()).andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    /**
     * Test using the Example status list values to ensure that statuses are detected correctly
     * Index 0: REVOKED (1)
     * Index 1: SUSPENDED (2)
     * Index 3: Custom Status (3)
     */
    @ParameterizedTest
    @CsvSource(value = {"0:1", "1:2", "3:3"}, delimiter = ':')
    void shouldSucceedVerifyingSDJWTCredentialWithSD_thenFail(Integer index, Integer tokenStatusListStatusCode) throws Exception {
        // Integer index = "".equals(input) ? null : Integer.parseInt(input);
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
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response, so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isOk()).andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        var evaluations = managementEntity.getCredentialEvaluation();
        assertThat(evaluations).as("In DCQL query only has a single credential").hasSize(1);
        var presentationEvaluation = evaluations.values().stream().findFirst().get();
        assertThat(presentationEvaluation).as("Only a single VC was presented").hasSize(1);
        var credentialStatus = presentationEvaluation.getFirst().credentialStatus();
        assertThat(credentialStatus.valid()).as("Credential was revoked and should not be valid").isFalse();
        assertThat(credentialStatus.status()).as("Status should be propagated to business component").isEqualTo(tokenStatusListStatusCode);
    }

    @Test
    void shouldRejectDCQLPresentation_whenCredentialIsRevoked_thenAccepted_statusFailed() throws Exception {
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
        var boundSdJwt = emulator.addKeyBindingProof(sdJwt, NONCE_SD_JWT_SQL, "decentralized_identifier:did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var vpTokenMap = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(boundSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpTokenMap);

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, submissionData, REQUEST_ID_SECURED)
                .andExpect(status().isOk());
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).as("The status was revoked, the suggested status is failed").isEqualTo(VerificationStatus.FAILED);
        var evaluations = managementEntity.getCredentialEvaluation();
        assertThat(evaluations).as("DCQL Query only has a single credential").hasSize(1);
        var presentationEvaluation = evaluations.values().stream().findFirst().get();
        assertThat(presentationEvaluation).as("Only a single VC was presented").hasSize(1);
        var credentialStatus = presentationEvaluation.getFirst().credentialStatus();
        assertThat(credentialStatus.valid()).as("Credential was revoked and should not be valid").isFalse();
        assertThat(credentialStatus.status()).as("Status should be propagated to business component").isEqualTo(1);
    }

    @Test
    void twoTimesSameDisclosures_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var parts = sdJWT.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER);

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER
                + StringUtils.join(sd, SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER) + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER
                + StringUtils.join(sd, SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER) + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "decentralized_identifier:did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_transaction_data"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }


    @Test
    @Disabled("Management entity REQUEST_DIFFERENT_KB_ALGS was migrated to DCQL format and no longer enforces " +
            "kb-jwt algorithm restrictions. The presentationDefinitionJsonDiffKbAlgs() config is unused.")
    void wrongKeyBindingAlgorithm_thenError() throws Exception {

        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);
        mockDidResolverResponse(emulator);

        var response = postVerificationResponse(REQUEST_DIFFERENT_KB_ALGS, vpToken, REQUEST_DIFFERENT_KB_ALGS)
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
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("SD-JWT claims are not valid"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, managementEntity.getWalletResponse().errorCode());
    }

    @Test
    void sdJWTExpired_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);


        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("SD-JWT claims are not valid"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, managementEntity.getWalletResponse().errorCode());
    }

    @Test
    void sdJWTAdditionalDisclosure_thenError() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var additionalDisclosure = new Disclosure("additional", "definetly_wrong");
        var newCred = sdJWT + additionalDisclosure + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "decentralized_identifier:did:example:12345");


        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("Unused disclosures detected"));

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, managementEntity.getWalletResponse().errorCode());
    }

    @Test
    void shouldVerifyNestedSDJWTCredentialSD_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "decentralized_identifier:did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", REQUEST_ID_SECURED));

        sendVerificationResponse(String.format(responseDataUriFormat, REQUEST_ID_SECURED), vpToken, requestObject)
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {DcqlTestHelper.VC_SD_JWT_CREDENTIAL_FORMAT, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT})
    void shouldSucceedVerifyingCredentialWithLegacyCNFFormat_thenSuccess(String credentialFormat) throws Exception {

        var requestId = UUID.randomUUID();
        managementEntityRepository.save(Management.builder()
                .id(requestId)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .oauthState(requestId.toString())
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of(DEFAULT_ISSUER_ID))
                .jwtSecuredAuthorizationRequest(true)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQueryJson(credentialFormat)))
                .build());

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock(true, credentialFormat);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, "decentralized_identifier:did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", REQUEST_ID_SECURED));

        sendVerificationResponse(String.format(responseDataUriFormat, REQUEST_ID_SECURED), vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri").doesNotExist()).andReturn();
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
        var parts = sdJWT.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER);

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER + StringUtils.join(sd, SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER) + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("Status List Token malformed"));
    }


    @Test
    void shouldVerifyingSDJWTCredentialSDWithDifferentPrivateKey_thenException() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("SD-JWT claims are not valid"));
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
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description", containsString("Status List Token malformed")))
                .andReturn();

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void statusListResponseBodyTooBig_thenException() throws Exception {

        // GIVEN
        var expectedErrorMesssage = "Status list size from %s exceeds maximum allowed size".formatted("https://example.com/statuslists/1");
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        // ContetLengthInterceptor throws invalid argument exception if status list is too big
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT))
                .thenThrow(new StatusListMaxSizeExceededException(expectedErrorMesssage));

        var sdJWT = emulator.createSDJWTMock(100);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
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
        // Use window + 10s to reliably be outside the acceptable proof time window
        // (boundary at exactly window seconds is not considered expired: isBefore is strict)
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix,
                Instant.now().minusSeconds(verificationProperties.getAcceptableProofTimeWindowSeconds() + 10).getEpochSecond(),
                "kb+jwt");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, dcqlVpToken, REQUEST_ID_SECURED)
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void testDCQLEndpoint_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var parts = unsignedSdJwt.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER);
        var disclosures = Arrays.copyOfRange(parts, 1, parts.length);
        var discList = new java.util.ArrayList<>(Arrays.asList(disclosures));
        // remove index 2 first, then index 1 to keep indices stable
        discList.remove(2);
        discList.remove(1);
        var rebuiltSdJwt = parts[0]
                + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER
                + StringUtils.join(discList, SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER)
                + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;

        var sdJwt = emulator.addKeyBindingProof(rebuiltSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));

        // remove unused list disclosures
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, submissionData, REQUEST_ID_SECURED)
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
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());

        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING, submissionData, REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING)
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    @Test
    void testDCQLNestedEndpoint_withKeybinding_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createNestedSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());

        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_NESTED_SECURED, submissionData, REQUEST_ID_NESTED_SECURED)
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
                      "format": "%s",
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
                """.formatted(dcqlCredentialId, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT, SDJWTCredentialMock.DEFAULT_VCT);

        var mgmt = managementEntityRepository.save(Management.builder()
                .id(dcqlCredentialId)
                .jwtSecuredAuthorizationRequest(false)
                .requestNonce(NONCE_SD_JWT_SQL)
                .state(PENDING)
                .oauthState(UUID.randomUUID().toString())
                .walletResponse(null)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQuery(dcqlQuery))
                .acceptedIssuerDids(List.of(DEFAULT_ISSUER_ID))
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
        var sdjwtWithoutKeyBinding = emulator.createSdJWT(builder, disclosures, null, null, null, DEFAULT_VCT, false, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT, JWSAlgorithm.ES256, false);
        var test = sdjwtWithoutKeyBinding.split("~")[0]
                .concat(used.stream().map(disc -> "~" + disc.toString()).reduce("", String::concat))
                .concat("~");

        var sdJwt = emulator.addKeyBindingProof(test, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());

        mockDidResolverResponse(emulator);
        var vpToken = Map.of(dcqlCredentialId, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(dcqlCredentialId, submissionData, UUID.fromString(mgmt.getOauthState()))
                .andExpect(status().isOk());
    }

    @Test
    void testDCQLEndpoint_withKeybindingButNotRequested_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING, submissionData, REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING)
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
        postVerificationResponse(REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING, submissionData, REQUEST_ID_WITH_DCQL_AND_OPTIONAL_HOLDER_BINDING)
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
        postVerificationResponse(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING, submissionData, REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING)
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
        postVerificationResponse(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING, submissionData, REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING)
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
        postVerificationResponse(REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING, submissionData, REQUEST_ID_WITH_DCQL_AND_HOLDER_BINDING)
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
        postVerificationResponse(REQUEST_ID_SECURED, submissionData, REQUEST_ID_SECURED)
                .andExpect(jsonPath("$.error_description").value("Key binding JWT is invalid"))
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
        postVerificationResponse(REQUEST_ID_SECURED, submissionData, REQUEST_ID_SECURED)
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
        var parts = sdJwt.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER);
        sdJwt = parts[0] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER + parts[1] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER + parts[parts.length - 1];
        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, submissionData, REQUEST_ID_SECURED)
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
        var parts = unsignedSdJwt.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER);
        // Only have the first claim (first_name) as disclosure
        unsignedSdJwt = parts[0] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER + parts[1] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        // Sign the presentation
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "http://localhost");

        // mock did resolver response so we get a valid public key for the issuer
        mockDidResolverResponse(emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(REQUEST_ID_SECURED, submissionData, REQUEST_ID_SECURED)
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
        postVerificationResponse(REQUEST_ID_SECURED, encryptedResponse, REQUEST_ID_SECURED)
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
        postVerificationErrorResponse(REQUEST_ID_SECURED, REQUEST_ID_SECURED, "access_denied", errorDescription)
                .andExpect(status().isOk());

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorDescription()).isEqualTo(errorDescription);
    }

    @Test
    void shouldHandleClientRejectionWithOnlyError() throws Exception {
        // WHEN / THEN
        mockMvc.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", REQUEST_ID_SECURED))
                        .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                        .header("SWIYU-API-Version", VPApiVersion.V1.getValue())
                        .formField("state", REQUEST_ID_SECURED.toString())
                        .formField("error", "client_rejected"))
                .andExpect(status().isOk());

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    @Test
    void shouldHandleClientRejectionWithEmptyErrorDescription() throws Exception {
        // WHEN / THEN
        postVerificationErrorResponse(REQUEST_ID_SECURED, REQUEST_ID_SECURED, "vp_formats_not_supported", "")
                .andExpect(status().isOk());

        // Verify that the management entity is marked as failed due to client rejection
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorDescription()).isEmpty();
    }

    @Test
    void shouldFailClientRejectionOnExpiredRequest() throws Exception {
        // WHEN / THEN
        postVerificationErrorResponse(REQUEST_ID_EXPIRED, REQUEST_ID_EXPIRED, "access_denied", "User cancelled")
                .andExpect(status().isGone());

        // Verify that the management entity state remains unchanged
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_EXPIRED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    @Test
    void shouldFailClientRejectionWithInvalidErrorType() throws Exception {
        // WHEN / THEN
        postVerificationErrorResponse(REQUEST_ID_SECURED, REQUEST_ID_SECURED, "invalid_error_type", "Some description")
                .andExpect(status().isBadRequest());

        // Verify that the management entity remains in pending state
        var managementEntity = managementEntityRepository.findById(REQUEST_ID_SECURED).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    @Test
    void shouldHandleConcurrentVerificationRequests_whenExternalDependencyBlocks() throws Exception {

        final SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        final String sdJwt = emulator.createSDJWTMock();
        String vpToken = emulator.addKeyBindingProof(sdJwt, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        final String finalVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        final CountDownLatch didCallStarted = new CountDownLatch(1);

        // Simulate did resolution blocking
        when(didResolverFacade.resolveKey(emulator.getKidHeaderValue()))
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
        executor.submit(() -> {
            try {
                postVerificationResponse(REQUEST_ID_SECURED, finalVpToken, REQUEST_ID_SECURED);
            } catch (Exception ignored) {}
        });

        assertThat(didCallStarted.await(5, TimeUnit.SECONDS)).isTrue();

        final int activeConnections = pool.getActiveConnections();

        assertThat(activeConnections)
                .as("No JDBC connection must be leaked even if external call never returns")
                .isZero();

        executor.shutdownNow();
    }

    @Disabled("EIDOMNI-962: Race condition in test design - pool.getActiveConnections() is checked before " +
            "all submitted threads have actually started, making the assertion trivially true and the test unreliable.")
    @Test
    void shouldNotDeadlockVerificationFlow_whenExternalDependencyBlocks() throws Exception {
        final int concurrentRequests = 5;
        final SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        final String sdJwt = emulator.createSDJWTMock();
        String vpToken = emulator.addKeyBindingProof(sdJwt, NONCE_SD_JWT_SQL, clientIdWithPrefix);
        vpToken = SDJWTCredentialMock.createMultipleVPTokenMock(vpToken);


        final CountDownLatch didCallStarted = new CountDownLatch(concurrentRequests);
        final CountDownLatch allowDidToFinish = new CountDownLatch(concurrentRequests);

        // Simulate did resolution blocking
        when(didResolverFacade.resolveKey(emulator.getKidHeaderValue()))
                .thenAnswer(invocation -> {
                    didCallStarted.countDown();
                    try {
                        allowDidToFinish.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    // Parse the JSON Web Key string into a Nimbus JWK object to ensure correct type
                    return JWK.parse(KeyFixtures.issuerPublicKeyAsJsonWebKey());
                });

        final HikariPoolMXBean pool = hikariPool();

        final ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 0; i < concurrentRequests; i++) {
            final String finalVpToken = vpToken;
            executor.submit(() -> {
                try {
                    mockMvc.perform(
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

    private @NonNull ResultActions postVerificationResponse(UUID requestObjectId, String dcqlVpToken, UUID state) throws Exception {
        return mockMvc.perform(post(String.format(responseDataUriFormat, requestObjectId))
                .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                .formField("state", state.toString())
                .formField("vp_token", dcqlVpToken));
    }

    private @NonNull ResultActions postVerificationErrorResponse(UUID requestObjectId, UUID state, String error, String errorDescription) throws Exception {
        return mockMvc.perform(post(String.format("/oid4vp/api/request-object/%s/response-data", requestObjectId))
                .contentType(APPLICATION_FORM_URLENCODED_VALUE)
                .formField("state", state.toString())
                .formField("error", error)
                .formField("error_description", errorDescription));
    }

    private ResultActions getManagementObjectById(String requestObjectId) throws Exception {
        return mockMvc.perform(get("/management/api/verifications/" + requestObjectId));
    }

    private static Stream<Arguments> dcqlQueryProvider() {
        return Stream.of(
                Arguments.of(0, "CH", 4, 6),
                Arguments.of(1, "GB", 1, 3)
        );
    }

    private ManagementResponseDto getAddressArrayManagement(int expectedIndex, String expectedCountry) {
        var dcqlQuery = """
                {
                "credentials": [
                    {
                      "id": "%s",
                      "format": "%s",
                      "meta": {
                        "vct_values": [ "%s" ]
                      },
                      "claims": [
                          {"path": ["addresses", %s, "country"], "values": ["%s"]}
                      ]
                    }
                  ]
                }
                """.formatted(DEFAULT_DCQL_CREDENTIAL_ID, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT, SDJWTCredentialMock.DEFAULT_VCT, expectedIndex, expectedCountry);

        var createVerificationManagementDto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of(DEFAULT_ISSUER_ID))
                .jwtSecuredAuthorizationRequest(true)
                .responseMode(ResponseModeTypeDto.DIRECT_POST_JWT)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQueryDto(dcqlQuery))
                .build();

        return createVerificationRequest(mockMvc, createVerificationManagementDto);
    }
}
