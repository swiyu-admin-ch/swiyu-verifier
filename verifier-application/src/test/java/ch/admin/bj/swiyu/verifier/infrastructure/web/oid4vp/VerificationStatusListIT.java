package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.sdjwtutil.SdJwtConstants;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidResolverTestHelper;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListMaxSizeExceededException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolver;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.VerificationManagementSeeder.DEFAULT_DCQL_CREDENTIAL_ID;
import static ch.admin.bj.swiyu.verifier.VerificationManagementSeeder.FIXTURES;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.StatusListGenerator.stubStatusListResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the status list error handling of the response-data endpoint and of the resulting management state.
 */
public class VerificationStatusListIT extends AbstractVerificationControllerIT {

    private final String clientId = "did:example:12345";
    private final String prefix = "decentralized_identifier";
    private final String clientIdWithPrefix = prefix + ":" + clientId;

    @MockitoBean
    private StatusListResolver mockedStatusListResolverAdapter;

    /**
     * Verifies that an index outside the status list bounds yields HTTP 400 and a FAILED verification.
     */
    @Test
    void givenStatusListIndexOutOfBounds_whenPostVerificationResponse_thenBadRequest() throws Exception {

        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        stubStatusListResolver(mockedStatusListResolverAdapter, emulator);

        var sdJWT = emulator.createSDJWTMock(100);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description", containsString("Status List Token malformed")))
                .andReturn();

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    /**
     * Verifies that a tampered status list signature yields HTTP 400 with {@code invalid_transaction_data}.
     */
    @Test
    void givenInvalidStatusListSignature_whenPostVerificationResponse_thenBadRequest() throws Exception {
        var statusListIndex = 2;
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        stubStatusListResolver(mockedStatusListResolverAdapter, StatusListGenerator.SPEC_STATUS_LIST,
                emulator.getHolderKey(), emulator.getIssuerId(), emulator.getKidHeaderValue());

        var sdJWT = emulator.createSDJWTMock(statusListIndex);
        var parts = sdJWT.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER);

        var sd = Arrays.copyOfRange(parts, 1, parts.length - 2);
        var newCred = parts[0] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER + StringUtils.join(sd, SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER) + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER;
        var vpToken = emulator.addKeyBindingProof(newCred, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description").value("Status List Token malformed"));
    }

    /**
     * Verifies that an oversized status list yields HTTP 400 and {@code UNRESOLVABLE_STATUS_LIST}.
     */
    @Test
    void givenStatusListTooBig_whenPostVerificationResponse_thenBadRequest() throws Exception {

        // GIVEN
        var expectedErrorMesssage = "Status list size from %s exceeds maximum allowed size".formatted("https://example.com/statuslists/1");
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        // StatusListResolver throws StatusListMaxSizeExceededException if the status list is too big
        when(mockedStatusListResolverAdapter.resolveStatusList(StatusListGenerator.SPEC_SUBJECT))
                .thenThrow(new StatusListMaxSizeExceededException(expectedErrorMesssage));

        var sdJWT = emulator.createSDJWTMock(100);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error").value("invalid_transaction_data"))
                .andExpect(jsonPath("error_description", containsString(expectedErrorMesssage)))
                .andReturn();

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().errorCode()).isEqualTo(VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST);
    }

    /**
     * Verifies that a revoked credential is reported as invalid with status 1 and a FAILED verification.
     */
    @Test
    void givenRevokedCredential_whenPostVerificationResponse_thenStatusFailed() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        // idx=0 has status value 1 (revoked) in SPEC_STATUS_LIST
        stubStatusListResolver(mockedStatusListResolverAdapter, emulator);

        var sdJwt = emulator.createSDJWTMock(0);
        var boundSdJwt = emulator.addKeyBindingProof(sdJwt, NONCE_SD_JWT_SQL, "decentralized_identifier:did:example:12345");

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var vpTokenMap = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(boundSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpTokenMap);

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), submissionData, FIXTURES.secured())
                .andExpect(status().isOk());
        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).as("The status was revoked, the suggested status is failed").isEqualTo(VerificationStatus.FAILED);
        var evaluations = managementEntity.getCredentialEvaluation();
        assertThat(evaluations).as("DCQL Query only has a single credential").hasSize(1);
        var presentationEvaluation = evaluations.values().stream().findFirst().get();
        assertThat(presentationEvaluation).as("Only a single VC was presented").hasSize(1);
        var credentialStatus = presentationEvaluation.getFirst().credentialStatus();
        assertThat(credentialStatus.valid()).as("Credential was revoked and should not be valid").isFalse();
        assertThat(credentialStatus.status()).as("Status should be propagated to business component").isEqualTo(1);
    }

    /**
     * Verifies that the revoked (1), suspended (2) and custom (3) status codes are propagated and yield FAILED.
     */
    @ParameterizedTest
    @CsvSource(value = {"0:1", "1:2", "3:3"}, delimiter = ':')
    void givenStatusEntryWithNonZeroCode_whenPostVerificationResponse_thenStatusFailedWithPropagatedCode(
            Integer index, Integer tokenStatusListStatusCode) throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();

        stubStatusListResolver(mockedStatusListResolverAdapter, emulator);

        var sdJWT = emulator.createSDJWTMock(index);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response, so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isOk())
                .andReturn();

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        var evaluations = managementEntity.getCredentialEvaluation();
        assertThat(evaluations).as("In DCQL query only has a single credential").hasSize(1);
        var presentationEvaluation = evaluations.values().stream().findFirst().get();
        assertThat(presentationEvaluation).as("Only a single VC was presented").hasSize(1);
        var credentialStatus = presentationEvaluation.getFirst().credentialStatus();
        assertThat(credentialStatus.valid()).as("Credential was revoked and should not be valid").isFalse();
        assertThat(credentialStatus.status()).as("Status should be propagated to business component").isEqualTo(tokenStatusListStatusCode);
    }

    /**
     * Verifies that a credential without status list index and one with a valid index both yield SUCCESS.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "2"})
    void givenAbsentOrValidStatusListIndex_whenPostVerificationResponse_thenSuccess(String statusListIndexInput) throws Exception {
        Integer statusListIndex = "".equals(statusListIndexInput) ? null : Integer.parseInt(statusListIndexInput);
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        stubStatusListResolver(mockedStatusListResolverAdapter, emulator);

        var sdJWT = emulator.createSDJWTMock(statusListIndex);
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isOk())
                .andReturn();

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }
}
