package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.sdjwtutil.SdJwtConstants;
import ch.admin.bj.swiyu.verifier.common.DcqlTestHelper;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.KeyOnlySignatureConfiguration;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.TrustMethod;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.dto.management.ConfigurationOverrideDto;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidResolverTestHelper;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolver;
import com.authlete.sd.Disclosure;
import com.authlete.sd.SDObjectBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
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

import static ch.admin.bj.swiyu.verifier.VerificationManagementSeeder.*;
import static ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus.PENDING;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Verifies the OID4VP verification flow of the request-object and response-data endpoints end to end.
 */
class VerificationControllerIT extends AbstractVerificationControllerIT {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * List of request object ids used by parameterized tests with different verification requests.
     */
    private static final List<UUID> DEFAULT_REQUEST_OBJECT_SOURCE = List.of(FIXTURES.secured(), FIXTURES.sdjwtResponseEncrypted());

    @Autowired
    private ManagementRepository managementEntityRepository;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private StatusListResolver mockedStatusListResolverAdapter;

    private final String clientId = "did:example:12345";
    private final String prefix = "decentralized_identifier";
    private final String clientIdWithPrefix = prefix + ":" + clientId;

    /**
     * Verifies that a presentation sent to an expired management object returns HTTP 410 and leaves it PENDING.
     */
    @Test
    void givenExpiredManagement_whenPostVerificationResponse_thenGone() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var vpTokenMap = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken));
        var submissionData = objectMapper.writeValueAsString(vpTokenMap);

        // WHEN / THEN
        postVerificationResponse(FIXTURES.expired(), submissionData, FIXTURES.expired())
                .andExpect(status().isGone());

        var managementEntity = managementEntityRepository.findById(FIXTURES.expired()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    /**
     * Verifies that a valid credential submitted for a signed or encrypted request object yields a SUCCESS verification.
     */
    @ParameterizedTest
    @FieldSource("DEFAULT_REQUEST_OBJECT_SOURCE")
    void givenValidCredential_whenSendVerificationResponse_thenSuccess(UUID requestObjectId) throws Exception {
        assertThat(requestObjectId).isIn(DEFAULT_REQUEST_OBJECT_SOURCE); // Nonsense Assert to stop linters going insane about unused field
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", requestObjectId));

        sendVerificationResponse(String.format(RESPONSE_DATA_URI_FORMAT, requestObjectId), vpToken, requestObject)
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

    /**
     * Verifies that a nested credential carrying all disclosures returns only the requested address claim.
     */
    @ParameterizedTest
    @MethodSource("DCQLQueryProvider")
    void givenNestedCredentialWithAllDisclosures_whenSendVerificationResponse_thenOnlyRequestedClaimReturned(
            int expectedIndex,
            String expectedCountry) throws Exception {

        // credential_subject_data is only included in the management API response when enabled
        enableCredentialSubjectDataAuditInformation();

        var createResponseDto = getAddressArrayManagement(expectedIndex, expectedCountry, null);

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSimpleNestedSDJWTMock();

        List<String> list = new ArrayList<>(Arrays.asList(sdJWT.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER)));

        var fixedSDJWT = String.join(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER, list) + "~";
        var vpToken = emulator.addKeyBindingProof(fixedSDJWT, createResponseDto.requestNonce(), clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", createResponseDto.id()));

        sendVerificationResponse(String.format(RESPONSE_DATA_URI_FORMAT, createResponseDto.id()), vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri").doesNotExist()).andReturn();

        var mgmtResponse = getManagementObjectById(createResponseDto.id().toString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode walletResponse = objectMapper.readTree(mgmtResponse).get("wallet_response");
        var addresses = walletResponse.get("dcql_response").get(DEFAULT_DCQL_CREDENTIAL_ID).get("addresses.%d.country".formatted(expectedIndex)).asArray();

        // as all addresses are sent in disclosures, all addresses should be present in the verification result, even if some were not requested
        assertThat(addresses).hasSize(1);

        assertThat(addresses).as("Only requested index should be provided").hasSize(1);
        assertThat(addresses.get(0).asString()).isEqualTo(expectedCountry);
    }

    /**
     * Verifies that a nested credential disclosing only the requested address still returns that claim.
     */
    @ParameterizedTest
    @MethodSource("DCQLQueryProvider")
    void givenNestedCredentialWithMinimalDisclosures_whenSendVerificationResponse_thenOnlyRequestedClaimReturned(
            int expectedIndex,
            String expectedCountry,
            int disclosureIndexStart,
            int disclosureIndexEnd) throws Exception {

        // credential_subject_data is only included in the management API response when explicitly enabled (EIDOMNI-1321)
        enableCredentialSubjectDataAuditInformation();

        var createResponseDto = getAddressArrayManagement(expectedIndex, expectedCountry, null);

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSimpleNestedSDJWTMock();

        // remove fist 2 disclosures to simulate a holder that only discloses the second address
        List<String> list = new ArrayList<>(Arrays.asList(sdJWT.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER)));
        list.subList(disclosureIndexStart, disclosureIndexEnd).clear();

        var fixedSdjwt = String.join(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER, list) + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(fixedSdjwt, createResponseDto.requestNonce(), clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", createResponseDto.id()));

        sendVerificationResponse(String.format(RESPONSE_DATA_URI_FORMAT, createResponseDto.id()), vpToken, requestObject)
                .andExpect(status().isOk());

        var mgmtResponse = getManagementObjectById(createResponseDto.id().toString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode walletResponse = objectMapper.readTree(mgmtResponse).get("wallet_response");
        var addresses = walletResponse.get("dcql_response").get(DEFAULT_DCQL_CREDENTIAL_ID).get("addresses.%d.country".formatted(expectedIndex)).asArray();

        assertThat(addresses).as("Only requested index should be provided").hasSize(1);
        assertThat(addresses.get(0).asString()).isEqualTo(expectedCountry);
    }

    /**
     * Verifies that a null claim path returns all matching address claims
     */
    @Test
    void givenNullClaimPath_whenSendVerificationResponse_thenAllMatchingClaimsReturned() throws Exception {
        // credential_subject_data is only included in the management API response when explicitly enabled (EIDOMNI-1321)
        enableCredentialSubjectDataAuditInformation();

        var createResponseDto = getAddressArrayManagement(null, "CH", null);

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSimpleNestedSDJWTMock();

        // hand over the entire sdJwt

        var vpToken = emulator.addKeyBindingProof(sdJWT, createResponseDto.requestNonce(), clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", createResponseDto.id()));

        sendVerificationResponse(String.format(RESPONSE_DATA_URI_FORMAT, createResponseDto.id()), vpToken, requestObject)
                .andExpect(status().isOk());

        var mgmtResponse = getManagementObjectById(createResponseDto.id().toString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode walletResponse = objectMapper.readTree(mgmtResponse).get("wallet_response");
        var presentedValues = walletResponse.get("dcql_response").get(DEFAULT_DCQL_CREDENTIAL_ID);
        assertThat(presentedValues).as("Only one claim was requested in the dcql query").hasSize(1);
        var addresses = presentedValues.get("addresses.null.country").asArray();

        assertThat(addresses).as("Both options are returned").hasSize(2);
        assertThat(addresses.valueStream().map(JsonNode::asString)).containsExactly("CH", "GB");
    }

    /**
     * Verifies that a requested address object missing from the credential yields HTTP 400 with a missing claim error.
     */
    @Test
    void givenNestedCredentialMissingRequestedClaim_whenSendVerificationResponse_thenBadRequest() throws Exception {
        var notPresentIndex = 0;
        var createResponseDto = getAddressObjectManagement(notPresentIndex);

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSimpleNestedSDJWTMock();

        List<String> list = new ArrayList<>(Arrays.asList(sdJWT.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER)));

        int[] removeIdx = {3, 2, 1};
        for (int idx : removeIdx) {
            if (idx < list.size()) {
                list.remove(idx);
            }
        }

        var fixedSDJWT = String.join(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER, list) + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(fixedSDJWT, createResponseDto.requestNonce(), clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", createResponseDto.id()));

        sendVerificationResponse(String.format(RESPONSE_DATA_URI_FORMAT, createResponseDto.id()), vpToken, requestObject)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_description").value("Requested DCQL path could not be found - Missing claim at index %s".formatted(notPresentIndex)))
                .andReturn();
    }

    /**
     * Verifies that a DCQL path selecting a single array element is resolved and the verification succeeds.
     */
    @Test
    void givenRequestedArrayElement_whenSendVerificationResponse_thenSuccess() throws Exception {
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
        var sdjwtWithoutKeyBinding = emulator.createSdJWT(builder, disclosures, null, null, null, SDJWTCredentialMock.DEFAULT_VCT, false, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT, JWSAlgorithm.ES256, false);
        var sdJwtWithoutDuplicates = sdjwtWithoutKeyBinding.split("~")[0]
                .concat(used.stream().map(disc -> "~" + disc.toString()).reduce("", String::concat))
                .concat("~");

        var sdJwt = emulator.addKeyBindingProof(sdJwtWithoutDuplicates, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());

        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(dcqlCredentialId, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);

        // WHEN / THEN
        postVerificationResponse(dcqlCredentialId, submissionData, UUID.fromString(mgmt.getOauthState()))
                .andExpect(status().isOk());
    }

    /**
     * Verifies that a credential issued in the future is rejected with HTTP 400 and a FAILED verification.
     */
    @Test
    void givenFutureIssuanceTime_whenPostVerificationResponse_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(Instant.now().plus(7, ChronoUnit.DAYS).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("SD-JWT claims are not valid"));

        checkIfManagementEntityFailedAndWalletResponseIsNotDefined(FIXTURES.secured());
    }

    /**
     * Verifies that an expired credential is rejected with HTTP 400 and a FAILED verification.
     */
    @Test
    void givenExpiredCredential_whenPostVerificationResponse_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        var sdJWT = emulator.createSDJWTMock(null, Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond());
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);


        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("SD-JWT claims are not valid"));

        checkIfManagementEntityFailedAndWalletResponseIsNotDefined(FIXTURES.secured());
    }

    /**
     * Verifies that an unused additional disclosure is rejected as MALFORMED_CREDENTIAL.
     */
    @Test
    void givenUnusedAdditionalDisclosure_whenPostVerificationResponse_thenMalformedCredential() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        var additionalDisclosure = new Disclosure("additional", "definetly_wrong");
        var newCred = sdJWT + additionalDisclosure + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, "decentralized_identifier:did:example:12345");


        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("Unused disclosures detected"));

        var managementEntity = checkIfManagementEntityFailedAndWalletResponseIsNotDefined(FIXTURES.secured());
        assertEquals(VerificationErrorResponseCode.MALFORMED_CREDENTIAL, managementEntity.getWalletResponse().errorCode());
    }

    /**
     * Verifies that presenting the same disclosures twice is rejected with HTTP 400.
     */
    @Test
    void givenDuplicatedDisclosures_whenPostVerificationResponse_thenBadRequest() throws Exception {
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
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_transaction_data"));

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    /**
     * Verifies that both supported legacy cnf credential formats are accepted.
     */
    @ParameterizedTest
    @ValueSource(strings = {DcqlTestHelper.VC_SD_JWT_CREDENTIAL_FORMAT, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT})
    void givenLegacyCnfCredentialFormat_whenSendVerificationResponse_thenSuccess(String credentialFormat) throws Exception {

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
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", FIXTURES.secured()));

        sendVerificationResponse(String.format(RESPONSE_DATA_URI_FORMAT, FIXTURES.secured()), vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri").doesNotExist()).andReturn();
    }

    /**
     * Verifies that a credential whose signature does not match the resolved issuer key is rejected with HTTP 400.
     */
    @Test
    void givenCredentialSignedWithDifferentKey_whenPostVerificationResponse_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock(new ECKeyGenerator(Curve.P_256).generate());
        var sdJWT = emulator.createSDJWTMock();
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("SD-JWT claims are not valid"));
    }

    /**
     * Verifies that a credential with surplus disclosures trimmed still succeeds and keeps the required claim data.
     */
    @Test
    void givenCredentialWithUnrequestedDisclosuresRemoved_whenPostVerificationResponse_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
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
        postVerificationResponse(FIXTURES.secured(), submissionData, FIXTURES.secured())
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);

        assertThat(managementEntity.getWalletResponse().credentialSubjectData())
                .contains("first_name")
                .contains("TestFirstname")
                .contains("last_name")
                .contains("TestLastName")
                .contains("languages");
    }

    /**
     * Verifies that a malformed vp_token yields HTTP 400 and leaves the request unconsumed (PENDING).
     */
    @Test
    void givenMalformedVpToken_whenPostVerificationResponse_thenBadRequestAndRequestNotConsumed() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "http://localhost");

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, sdJwt);
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), submissionData, FIXTURES.secured())
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }
    /**
     * Verifies that a credential missing a requested claim yields HTTP 400 and a FAILED verification.
     */
    @Test
    void givenMissingRequestedClaim_whenPostVerificationResponse_thenBadRequest() throws Exception {
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
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), submissionData, FIXTURES.secured())
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").value("invalid_transaction_data"));

        checkIfManagementEntityFailedAndWalletResponseIsNotDefined(FIXTURES.secured());
    }

    /**
     * Verifies that an unparsable vp_token yields HTTP 400 and leaves the request PENDING.
     */
    @Test
    void givenUnparsableVpToken_whenPostVerificationResponse_thenBadRequestAndRequestNotConsumed() throws Exception {
        // GIVEN
        // Create encrypted DCQL response string
        String encryptedResponse = "eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMjU2R0NNIiwidHlwIjoiSldFIn0...";

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), encryptedResponse, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_description", startsWith("Failed to parse vp_token as DCQL format: Unrecognized token")));

        // Verify that the management entity remains in pending state since the exception is thrown early
        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(PENDING);
    }

    /**
     * Verifies that a blocking DID resolution does not leak JDBC connections during concurrent verification requests.
     */
    @Test
    void givenBlockingDidResolution_whenPostVerificationResponseConcurrently_thenNoJdbcConnectionLeaked() throws Exception {

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
                postVerificationResponse(FIXTURES.secured(), finalVpToken, FIXTURES.secured());
            } catch (Exception ignored) {
            }
        });

        assertThat(didCallStarted.await(5, TimeUnit.SECONDS)).isTrue();

        final int activeConnections = pool.getActiveConnections();

        assertThat(activeConnections)
                .as("No JDBC connection must be leaked even if external call never returns")
                .isZero();

        executor.shutdownNow();
    }

    /**
     * Verifies that a configuration override for the verifier DID and signing key is honoured and the verification succeeds.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void givenConfigurationOverride_whenSendVerificationResponse_thenSuccess(boolean needsOverride) throws Exception {
        // GIVEN
        var verifierDid = "did:webvh:mySCID12345213:identifier-reg.trust-infra.swiyu.admin.ch:api:v1:did:00000000-0000-0000-0000-000000000000";
        var verificationMethod = verifierDid + "#myVerificationMethod-xx";
        var key = createPemForKid(verificationMethod);
        var override = needsOverride ? new ConfigurationOverrideDto(null, verifierDid, verificationMethod, null, null, null) : null;
        var createResponseDto = getAddressArrayManagement(0, "CH", override);

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSimpleNestedSDJWTMock();

        var audSuffix = needsOverride ? verifierDid : clientId;
        var aud = prefix + ":" + audSuffix;
        var vpToken = emulator.addKeyBindingProof(sdJWT, createResponseDto.requestNonce(), aud);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        if (needsOverride) {
            var keyOnlySignatureConfiguration = new KeyOnlySignatureConfiguration();
            keyOnlySignatureConfiguration.setPrivateKey(key);
            keyOnlySignatureConfiguration.setVerificationMethod(verificationMethod);

            when(applicationProperties.getSigningKeys()).thenReturn(List.of(keyOnlySignatureConfiguration));
        }

        // WHEN / THEN
        var requestObject = getRequestObject(String.format("/oid4vp/api/request-object/%s", createResponseDto.id()));

        sendVerificationResponse(String.format(RESPONSE_DATA_URI_FORMAT, createResponseDto.id()), vpToken, requestObject)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect_uri").doesNotExist()).andReturn();

        var mgmtResponse = getManagementObjectById(createResponseDto.id().toString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode walletResponse = objectMapper.readTree(mgmtResponse).get("wallet_response");
        var addresses = walletResponse.get("dcql_response").get("defaultTestDcqlCredentialId").get("addresses.0.country").asArray();

        // Only selected index should be returned
        assertThat(addresses).hasSize(1);
    }

    /**
     * Fetches the management object through the management API by request object id.
     */
    private ResultActions getManagementObjectById(String requestObjectId) throws Exception {
        return mockMvc.perform(get("/management/api/verifications/" + requestObjectId));
    }

    /**
     * Provides the address index, country and disclosure range parameters for the nested credential tests.
     */
    private static Stream<Arguments> DCQLQueryProvider() {
        return Stream.of(
                Arguments.of(0, "CH", 4, 6),
                Arguments.of(1, "GB", 1, 3)
        );
    }

    /**
     * Creates a verification request that asks for a single nested address country claim.
     */
    private ManagementResponseDto getAddressArrayManagement(Integer expectedIndex, String expectedCountry, ConfigurationOverrideDto override) {
        var values = expectedCountry == null ? "" : ", \"values\": [\"" + expectedCountry + "\"]";
        var DCQLQuery = """
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
                          {"path": ["addresses", %s, "country"]%s}
                      ]
                    }
                  ]
                }
                """.formatted(DEFAULT_DCQL_CREDENTIAL_ID, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT, SDJWTCredentialMock.DEFAULT_VCT, expectedIndex, values);

        return createVerificationRequest(DCQLQuery, override);
    }

    /**
     * Creates a verification request that asks for the whole address object at the given array index.
     */
    private ManagementResponseDto getAddressObjectManagement(int expectedIndex) {
        var DCQLQuery = """
                {
                "credentials": [
                    {
                      "id": "%s",
                      "format": "%s",
                      "meta": {
                        "vct_values": [ "%s" ]
                      },
                      "claims": [
                          {"path": ["addresses", %s]}
                      ]
                    }
                  ]
                }
                """.formatted(DEFAULT_DCQL_CREDENTIAL_ID, DcqlTestHelper.DC_SD_JWT_CREDENTIAL_FORMAT, SDJWTCredentialMock.DEFAULT_VCT, expectedIndex);

        return createVerificationRequest(DCQLQuery, null);
    }

    /**
     * Creates a verification request through the management API with the given DCQL query and optional override.
     */
    private ManagementResponseDto createVerificationRequest(String DCQLQuery, ConfigurationOverrideDto override) {
        var createVerificationManagementDto = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(List.of(DEFAULT_ISSUER_ID))
                .jwtSecuredAuthorizationRequest(true)
                .responseMode(ResponseModeTypeDto.DIRECT_POST_JWT)
                .dcqlQuery(DcqlTestHelper.stringToDcqlQueryDto(DCQLQuery));

        if (override != null) {
            createVerificationManagementDto.configuration_override(override);
        }

        return createVerificationRequest(mockMvc, createVerificationManagementDto.build());
    }

    /**
     * Builds an EC private key in PEM format for the given verification method key id.
     */
    public static String createPemForKid(String kid) throws JOSEException {
        var ecKey = new ECKeyGenerator(Curve.P_256)
                .keyID(kid)
                .algorithm(JWSAlgorithm.ES256)
                .keyUse(KeyUse.SIGNATURE)
                .generate()
                .toECPrivateKey();

        return """
                -----BEGIN PRIVATE KEY-----
                %s
                -----END PRIVATE KEY-----
                """.formatted(
                Base64.getMimeEncoder(64, new byte[]{'\n'})
                        .encodeToString(ecKey.getEncoded())
        );
    }

    /**
     * Enables the {@code credential_subject_data} audit flag for the current test only (see EIDOMNI-1321).
     */
    private void enableCredentialSubjectDataAuditInformation() {
        var auditInformation = new ApplicationProperties.AdditionalAuditInformationProperties();
        auditInformation.setCredentialSubjectDataEnabled(true);
        when(applicationProperties.getAdditionalAuditInformation()).thenReturn(auditInformation);
    }

    /**
     * Asserts that the verification FAILED without credential subject data and returns the management entity.
     */
    private Management checkIfManagementEntityFailedAndWalletResponseIsNotDefined(UUID managementEntityId) {
        var managementEntity = managementEntityRepository.findById(managementEntityId).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().credentialSubjectData()).isNull();
        return managementEntity;
    }

    /**
     * Returns the Hikari connection pool MX bean of the test datasource.
     */
    private HikariPoolMXBean hikariPool() {
        return ((HikariDataSource) dataSource).getHikariPoolMXBean();
    }
}
