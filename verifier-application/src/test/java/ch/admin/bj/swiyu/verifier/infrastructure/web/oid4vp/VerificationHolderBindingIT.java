package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.sdjwtutil.SdJwtConstants;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidResolverTestHelper;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static ch.admin.bj.swiyu.verifier.VerificationManagementSeeder.DEFAULT_DCQL_CREDENTIAL_ID;
import static ch.admin.bj.swiyu.verifier.VerificationManagementSeeder.FIXTURES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the holder binding (key binding proof) rules of the response-data endpoint and the resulting management state.
 */
public class VerificationHolderBindingIT extends AbstractVerificationControllerIT {

    @Autowired
    private VerificationProperties verificationProperties;

    /**
     * Verifies that a key binding proof with the correct audience is accepted and the verification succeeds.
     */
    @Test
    void givenKeyBindingRequestedAndProvided_whenPostVerificationResponse_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());

        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.withDcqlAndHolderBinding(), submissionData, FIXTURES.withDcqlAndHolderBinding())
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(FIXTURES.withDcqlAndHolderBinding()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    /**
     * Verifies that a nested credential presented with a valid key binding proof yields a SUCCESS verification.
     */
    @Test
    void givenNestedCredentialAndKeyBinding_whenPostVerificationResponse_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createNestedSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());

        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);

        // WHEN / THEN
        postVerificationResponse(FIXTURES.nestedSecured(), submissionData, FIXTURES.nestedSecured())
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(FIXTURES.nestedSecured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    /**
     * Verifies that a key binding proof is accepted although the request only asks for optional holder binding.
     */
    @Test
    void givenOptionalKeyBindingAndProvided_whenPostVerificationResponse_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, applicationProperties.getClientIdWithPrefix());

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.withDcqlAndOptionalHolderBinding(), submissionData, FIXTURES.withDcqlAndOptionalHolderBinding())
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(FIXTURES.withDcqlAndOptionalHolderBinding()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    /**
     * Verifies that a credential without key binding proof is accepted when holder binding is optional.
     */
    @Test
    void givenOptionalKeyBindingAndNoneProvided_whenPostVerificationResponse_thenSuccess() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock(true);

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(unsignedSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.withDcqlAndOptionalHolderBinding(), submissionData, FIXTURES.withDcqlAndOptionalHolderBinding())
                .andExpect(status().isOk());

        var managementEntity = managementEntityRepository.findById(FIXTURES.withDcqlAndOptionalHolderBinding()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.SUCCESS);
    }

    /**
     * Verifies that a missing key binding proof is rejected with HTTP 400 although holder binding is required.
     */
    @Test
    void givenKeyBindingRequestedButMissing_whenPostVerificationResponse_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(unsignedSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.withDcqlAndHolderBinding(), submissionData, FIXTURES.withDcqlAndHolderBinding())
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(FIXTURES.withDcqlAndHolderBinding()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    /**
     * Verifies that a key binding proof with a wrong audience is rejected with HTTP 400 and a FAILED verification.
     */
    @Test
    void givenInvalidKeyBindingAudience_whenPostVerificationResponse_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "incorrect-audience");

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.withDcqlAndHolderBinding(), submissionData, FIXTURES.withDcqlAndHolderBinding())
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(FIXTURES.withDcqlAndHolderBinding()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    /**
     * Verifies that a credential without key binding proof is rejected when holder binding is required but impossible.
     */
    @Test
    void givenKeyBindingRequestedButNotPossible_whenPostVerificationResponse_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock(true);

        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(unsignedSdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.withDcqlAndHolderBinding(), submissionData, FIXTURES.withDcqlAndHolderBinding())
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(FIXTURES.withDcqlAndHolderBinding()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    /**
     * Verifies that a key binding proof with a request URI that does not match yields {@code holder_binding_mismatch}.
     */
    @Test
    void givenKeyBindingWithMismatchingRequestUri_whenPostVerificationResponse_thenHolderBindingMismatch() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "http://localhost:8080");

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), submissionData, FIXTURES.secured())
                .andExpect(jsonPath("$.error_description").value("Key binding JWT is invalid"))
                .andExpect(jsonPath("$.error_code").value("holder_binding_mismatch"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that an expired key binding proof is rejected with HTTP 400 and a FAILED verification.
     */
    @Test
    void givenExpiredKeyBindingProof_whenPostVerificationResponse_thenBadRequest() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var sdJWT = emulator.createSDJWTMock();
        // Use window + 10s to reliably be outside the acceptable proof time window
        // (boundary at exactly window seconds is not considered expired: isBefore is strict)
        String clientId = "did:example:12345";
        String prefix = "decentralized_identifier";
        String clientIdWithPrefix = prefix + ":" + clientId;
        var vpToken = emulator.addKeyBindingProof(sdJWT, NONCE_SD_JWT_SQL, clientIdWithPrefix,
                Instant.now().minusSeconds(verificationProperties.getAcceptableProofTimeWindowSeconds() + 10).getEpochSecond(),
                "kb+jwt");

        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);

        var dcqlVpToken = objectMapper.writeValueAsString(Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(vpToken)));

        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), dcqlVpToken, FIXTURES.secured())
                .andExpect(status().isBadRequest());

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
    }

    /**
     * Verifies that an altered SD-JWT yields {@code holder_binding_mismatch} and no credential subject data.
     */
    @Test
    void givenAlteredSdJwt_whenPostVerificationResponse_thenHolderBindingMismatch() throws Exception {
        // GIVEN
        SDJWTCredentialMock emulator = new SDJWTCredentialMock();
        var unsignedSdJwt = emulator.createSDJWTMock();
        var sdJwt = emulator.addKeyBindingProof(unsignedSdJwt, NONCE_SD_JWT_SQL, "decentralized_identifier:did:example:12345");
        // Split jwt, disclosures & binding proof
        var parts = sdJwt.split(SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER);
        sdJwt = parts[0] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER + parts[1] + SdJwtConstants.SD_JWT_PART_DELINEATION_CHARACTER + parts[parts.length - 1];
        // mock did resolver response so we get a valid public key for the issuer
        DidResolverTestHelper.stubResolveKey(didResolverFacade, emulator);
        var vpToken = Map.of(DEFAULT_DCQL_CREDENTIAL_ID, List.of(sdJwt));
        var submissionData = objectMapper.writeValueAsString(vpToken);
        // WHEN / THEN
        postVerificationResponse(FIXTURES.secured(), submissionData, FIXTURES.secured())
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").value("invalid_transaction_data"))
                .andExpect(jsonPath("$.error_code").value("holder_binding_mismatch"));

        var managementEntity = managementEntityRepository.findById(FIXTURES.secured()).orElseThrow();
        assertThat(managementEntity.getState()).isEqualTo(VerificationStatus.FAILED);
        assertThat(managementEntity.getWalletResponse().credentialSubjectData()).isNull();
    }
}
