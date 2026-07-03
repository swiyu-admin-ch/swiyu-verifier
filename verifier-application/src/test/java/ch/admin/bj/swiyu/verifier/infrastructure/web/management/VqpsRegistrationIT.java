package ch.admin.bj.swiyu.verifier.infrastructure.web.management;

import ch.admin.bj.swiyu.core.trust.client.api.VqpsSubmissionB2BApi;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsPublicationResult;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsSubmission;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsSubmissionStatus;
import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.domain.vqps.Vqps;
import ch.admin.bj.swiyu.verifier.domain.vqps.VqpsRepository;
import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.VerificationPurposeDto;
import ch.admin.bj.swiyu.verifier.service.management.fixtures.ApiFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the On-the-Fly vqPS registration flow (EIDOMNI-819).
 *
 * <p>What is tested: When a {@code POST /management/api/verifications} request is made
 * with a {@code verification_purpose} payload, the Verifier Service must call the
 * TMS B2B API (IF-014) to submit a vqPS and persist the resulting JWT in the DB cache.</p>
 *
 * <p>Boundary conditions: DB is empty at the start of each test.
 * The {@link VqpsSubmissionB2BApi} is mocked so no real TMS call is made.</p>
 *
 * <p>Expected result: HTTP 201 is returned, a vqPS entry is persisted in the DB for
 * the given scope, and the mock API is called exactly once for a new registration.</p>
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@TestPropertySource(properties = {
        "swiyu.trust-registry.tms-authoring-url=http://tms.example.com",
        "swiyu.trust-registry.vqps-expiry-buffer-seconds=0",
        "swiyu.trust-registry.oauth-token-url=http://auth.example.com/token",
        "swiyu.trust-registry.oauth-client-id=test-client",
        "swiyu.trust-registry.oauth-client-secret=test-secret"
})
class VqpsRegistrationIT {

    private static final String BASE_URL = "/management/api/verifications";
    private static final String TEST_SCOPE = "com.example.test_verification";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VqpsRepository vqpsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VqpsSubmissionB2BApi vqpsSubmissionB2BApi;

    @AfterEach
    void cleanup() {
        vqpsRepository.deleteAll();
    }

    /**
     * Happy path: a verification request with {@code verification_purpose} triggers a TMS B2B
     * submission, the returned vqPS JWT is persisted in the DB cache, and HTTP 201 is returned.
     */
    @Test
    void givenVerificationPurpose_whenCreatingVerification_thenVqpsIsPersistedInDb() throws Exception {
        String vqpsJwt = buildSignedVqpsJwt(TEST_SCOPE, Instant.now().plus(30, ChronoUnit.DAYS));
        mockTmsApiToReturnPublishedSubmission(vqpsJwt);

        var request = buildCreateRequestWithPurpose(TEST_SCOPE);

        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification_url").exists());

        var allCached = vqpsRepository.findAll();
        assertThat(allCached).hasSize(1);
        assertThat(allCached.getFirst().getJwt()).isEqualTo(vqpsJwt);
        assertThat(allCached.getFirst().getScope()).isEqualTo(TEST_SCOPE);

        verify(vqpsSubmissionB2BApi, times(1)).createVqpsSubmission(any());
    }

    /**
     * Cache hit: when a valid vqPS with a matching query hash already exists in the DB,
     * the TMS B2B API is NOT called again on a second identical request.
     */
    @Test
    void givenCachedVqps_whenCreatingVerification_thenTmsApiIsNotCalled() throws Exception {
        String vqpsJwt = buildSignedVqpsJwt(TEST_SCOPE, Instant.now().plus(30, ChronoUnit.DAYS));
        mockTmsApiToReturnPublishedSubmission(vqpsJwt);

        var request = buildCreateRequestWithPurpose(TEST_SCOPE);
        String requestJson = objectMapper.writeValueAsString(request);

        // First request – populates the cache with the correct hash
        mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isOk());

        // Second identical request – must hit the cache and NOT call TMS again
        mvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(requestJson))
                .andExpect(status().isOk());

        verify(vqpsSubmissionB2BApi, times(1)).createVqpsSubmission(any());
    }

    /**
     * Hash mismatch: when the cache contains a stale entry for a different hash (same scope),
     * the TMS B2B API IS called to register the updated query, and a new row is inserted.
     */
    @Test
    void givenStaleQueryHash_whenCreatingVerificationWithChangedQuery_thenTmsApiIsCalledAgain() throws Exception {
        // Pre-populate cache with a stale entry keyed by a hash that will never match
        String staleJwt = buildSignedVqpsJwt(TEST_SCOPE, Instant.now().plus(30, ChronoUnit.DAYS));
        vqpsRepository.save(
                Vqps.builder()
                        .queryHash("stale-hash-that-will-never-match")
                        .scope(TEST_SCOPE)
                        .jwt(staleJwt)
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond())
                        .build()
        );

        String freshJwt = buildSignedVqpsJwt(TEST_SCOPE, Instant.now().plus(30, ChronoUnit.DAYS));
        mockTmsApiToReturnPublishedSubmission(freshJwt);

        var request = buildCreateRequestWithPurpose(TEST_SCOPE);
        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Stale entry did not match → TMS must have been called once
        verify(vqpsSubmissionB2BApi, times(1)).createVqpsSubmission(any());

        // A new entry with the current hash must exist alongside the stale entry
        var allEntries = vqpsRepository.findAll();
        assertThat(allEntries).hasSize(2);
        assertThat(allEntries).anyMatch(e -> e.getJwt().equals(freshJwt));
        assertThat(allEntries).noneMatch(e -> e.getQueryHash().equals("stale-hash-that-will-never-match")
                && e.getJwt().equals(freshJwt));
    }

    /**
     * No-op: when {@code verification_purpose} is absent, the TMS B2B API is NOT called.
     */
    @Test
    void givenNoVerificationPurpose_whenCreatingVerification_thenTmsApiIsNotCalled() throws Exception {
        var request = ApiFixtures.createVerificationManagementWithDcqlQueryDto(
                ApiFixtures.getDcqlQueryDto(), java.util.List.of("did:example:123"));

        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(vqpsSubmissionB2BApi, never()).createVqpsSubmission(any());
    }

    @Test
    void givenNoDefaultValues_whenCreatingVerification_thenError() throws Exception {

        var purpose = VerificationPurposeDto.builder()
                .scope(TEST_SCOPE)
                .purposeName(Map.of("de-CH", "Testverifikation", "en", "Test Verification"))
                .purposeDescription(Map.of("de-CH", "Dies ist eine Testverifikation.", "en", "This is a test verification."))
                .build();

        var request = CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(java.util.List.of("did:example:123"))
                .dcqlQuery(ApiFixtures.getDcqlQueryDto())
                .verificationPurpose(purpose)
                .build();

        mvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_description").value("verificationPurpose.purposeDescription: must contain exactly one 'default' key with a non-blank value, verificationPurpose.purposeName: must contain exactly one 'default' key with a non-blank value"));

        verify(vqpsSubmissionB2BApi, never()).createVqpsSubmission(any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void mockTmsApiToReturnPublishedSubmission(String jwt) {
        var publicationResult = new VqpsPublicationResult()
                .jti(UUID.randomUUID().toString())
                .jwt(jwt)
                .expiresAt(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)));

        var submission = new VqpsSubmission()
                .id(UUID.randomUUID())
                .partnerId(UUID.randomUUID())
                .version(1L)
                .status(VqpsSubmissionStatus.PUBLICATION_SUCCEEDED)
                .publicationResult(publicationResult)
                .createdAt(new Date())
                .updatedAt(new Date());

        when(vqpsSubmissionB2BApi.createVqpsSubmission(any())).thenReturn(Mono.just(submission));
    }

    private Object buildCreateRequestWithPurpose(String scope) {
        var purpose = VerificationPurposeDto.builder()
                .scope(scope)
                .purposeName(Map.of("default", "Test verification", "de-CH", "Testverifikation", "en", "Test Verification"))
                .purposeDescription(Map.of("default", "This is a test", "de-CH", "Dies ist eine Testverifikation.", "en", "This is a test verification."))
                .build();

        return ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(java.util.List.of("did:example:123"))
                .dcqlQuery(ApiFixtures.getDcqlQueryDto())
                .verificationPurpose(purpose)
                .build();
    }

    /**
     * Builds a minimal signed JWT to simulate a vqPS returned by the TMS.
     */
    private String buildSignedVqpsJwt(String scope, Instant expiresAt) throws Exception {
        var ecKey = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
        var claimsSet = new JWTClaimsSet.Builder()
                .subject("did:example:verifier")
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(Date.from(expiresAt))
                .claim("scope", scope)
                .build();
        var signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("test-key").build(),
                claimsSet
        );
        signedJWT.sign(new ECDSASigner(ecKey));
        return signedJWT.serialize();
    }
}





