package ch.admin.bj.swiyu.verifier.service.vqps;
import ch.admin.bj.swiyu.core.trust.client.api.VqpsSubmissionB2BApi;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsPublicationResult;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsSubmission;
import ch.admin.bj.swiyu.core.trust.client.model.VqpsSubmissionStatus;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.domain.vqps.Vqps;
import ch.admin.bj.swiyu.verifier.domain.vqps.VqpsRepository;
import ch.admin.bj.swiyu.verifier.dto.management.VerificationPurposeDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
/**
 * Unit tests for {@link VqpsRegistrationService}.
 */
class VqpsRegistrationServiceTest {
    private static final String SCOPE = "com.example.test_scope";
    private static final String CLIENT_ID = "did:example:verifier";
    private static final long FAR_FUTURE_TTL = Instant.now().plus(1, ChronoUnit.DAYS).getEpochSecond();
    private TrustRegistryProperties trustRegistryProperties;
    private ApplicationProperties applicationProperties;
    private VqpsRepository vqpsRepository;
    private VqpsSubmissionB2BApi vqpsSubmissionB2BApi;
    private VqpsRegistrationService service;
    @BeforeEach
    void setUp() {
        trustRegistryProperties = mock(TrustRegistryProperties.class);
        applicationProperties = mock(ApplicationProperties.class);
        vqpsRepository = mock(VqpsRepository.class);
        vqpsSubmissionB2BApi = mock(VqpsSubmissionB2BApi.class);
        when(trustRegistryProperties.getVqpsExpiryBufferSeconds()).thenReturn(0L);
        when(applicationProperties.getClientId()).thenReturn(CLIENT_ID);
        when(vqpsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new VqpsRegistrationService(
                trustRegistryProperties,
                applicationProperties,
                vqpsRepository,
                vqpsSubmissionB2BApi,
                new ObjectMapper()
        );
    }
    @Test
    void getOrRegisterVqps_sameInput_producesSameHash() {
        var purpose = buildPurpose();
        var dcql = Map.of("credentials", "test");
        String jwt = buildJwt(Instant.now().plus(30, ChronoUnit.DAYS));
        mockTmsImmediateSuccess(jwt);
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        String hash1 = service.getOrRegisterVqps(purpose, dcql, FAR_FUTURE_TTL);
        reset(vqpsRepository, vqpsSubmissionB2BApi);
        when(vqpsRepository.findById(hash1)).thenReturn(Optional.of(
                Vqps.builder().queryHash(hash1).scope(SCOPE).jwt(jwt)
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond()).build()));
        String hash2 = service.getOrRegisterVqps(purpose, dcql, FAR_FUTURE_TTL);
        assertThat(hash1).isEqualTo(hash2);
        verifyNoInteractions(vqpsSubmissionB2BApi);
    }
    @Test
    void getOrRegisterVqps_differentPurposeName_producesDifferentHash() {
        String jwt = buildJwt(Instant.now().plus(30, ChronoUnit.DAYS));
        var dcql = Map.of("key", "value");
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        mockTmsImmediateSuccess(jwt);
        String hash1 = service.getOrRegisterVqps(VerificationPurposeDto.builder()
                .scope(SCOPE).purposeName(Map.of("en", "Name A")).purposeDescription(Map.of("en", "D")).build(),
                dcql, FAR_FUTURE_TTL);
        reset(vqpsRepository, vqpsSubmissionB2BApi);
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        mockTmsImmediateSuccess(jwt);
        String hash2 = service.getOrRegisterVqps(VerificationPurposeDto.builder()
                .scope(SCOPE).purposeName(Map.of("en", "Name B")).purposeDescription(Map.of("en", "D")).build(),
                dcql, FAR_FUTURE_TTL);
        assertThat(hash1).isNotEqualTo(hash2);
    }
    @Test
    void getOrRegisterVqps_withValidCachedEntry_returnsCachedHashWithoutTmsCall() {
        String jwt = buildJwt(Instant.now().plus(30, ChronoUnit.DAYS));
        var purpose = buildPurpose();
        var dcql = Map.of("credentials", "test");
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        mockTmsImmediateSuccess(jwt);
        String hash = service.getOrRegisterVqps(purpose, dcql, FAR_FUTURE_TTL);
        reset(vqpsRepository, vqpsSubmissionB2BApi);
        when(vqpsRepository.findById(hash)).thenReturn(Optional.of(
                Vqps.builder().queryHash(hash).scope(SCOPE).jwt(jwt)
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond()).build()));
        assertThat(service.getOrRegisterVqps(purpose, dcql, FAR_FUTURE_TTL)).isEqualTo(hash);
        verifyNoInteractions(vqpsSubmissionB2BApi);
    }
    @Test
    void getOrRegisterVqps_withNoCachedEntry_callsTmsAndPersistsEntry() {
        String jwt = buildJwt(Instant.now().plus(30, ChronoUnit.DAYS));
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        mockTmsImmediateSuccess(jwt);
        String hash = service.getOrRegisterVqps(buildPurpose(), Map.of("k", "v"), FAR_FUTURE_TTL);
        assertThat(hash).isNotBlank();
        verify(vqpsSubmissionB2BApi).createVqpsSubmission(any());
        verify(vqpsRepository).save(argThat(vqps ->
                vqps.getQueryHash().equals(hash)
                        && vqps.getScope().equals(SCOPE)
                        && vqps.getJwt().equals(jwt)));
    }
    @Test
    void getOrRegisterVqps_whenNewJwtExpiresTooSoon_throwsIllegalStateException() {
        String shortLivedJwt = buildJwt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        mockTmsImmediateSuccess(shortLivedJwt);
        long farFutureTtl = Instant.now().plus(60, ChronoUnit.DAYS).getEpochSecond();
        assertThatThrownBy(() -> service.getOrRegisterVqps(buildPurpose(), Map.of("k", "v"), farFutureTtl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expires at")
                .hasMessageContaining("before the verification TTL");
    }
    @Test
    void getOrRegisterVqps_withUnexpectedStatus_throwsIllegalStateException() {
        // With waitForPublication=true the TMS API responds synchronously – any non-terminal
        // status in the response is treated as unexpected (ACCEPTED should not occur anymore).
        VqpsSubmission unexpected = new VqpsSubmission()
                .id(UUID.randomUUID()).status(VqpsSubmissionStatus.ACCEPTED);
        when(vqpsSubmissionB2BApi.createVqpsSubmission(any())).thenReturn(Mono.just(unexpected));
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOrRegisterVqps(buildPurpose(), Map.of("k", "v"), FAR_FUTURE_TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected status");
    }
    @Test
    void getOrRegisterVqps_withPublicationFailed_throwsIllegalStateException() {
        VqpsSubmission failed = new VqpsSubmission()
                .id(UUID.randomUUID()).status(VqpsSubmissionStatus.PUBLICATION_FAILED);
        when(vqpsSubmissionB2BApi.createVqpsSubmission(any())).thenReturn(Mono.just(failed));
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOrRegisterVqps(buildPurpose(), Map.of("k", "v"), FAR_FUTURE_TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publication failed");
    }
    @Test
    void getOrRegisterVqps_withMalformedJwt_throwsIllegalStateException() {
        VqpsSubmission succeeded = new VqpsSubmission()
                .id(UUID.randomUUID()).status(VqpsSubmissionStatus.PUBLICATION_SUCCEEDED)
                .publicationResult(new VqpsPublicationResult().jwt("not.a.valid.jwt"));
        when(vqpsSubmissionB2BApi.createVqpsSubmission(any())).thenReturn(Mono.just(succeeded));
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOrRegisterVqps(buildPurpose(), Map.of("k", "v"), FAR_FUTURE_TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse vqPS JWT exp claim");
    }
    @Test
    void getOrRegisterVqps_withJwtMissingExpClaim_throwsIllegalStateException() throws Exception {
        var ecKey = new ECKeyGenerator(Curve.P_256).keyID("test").generate();
        var signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("test").build(),
                new JWTClaimsSet.Builder().subject("test").build());
        signedJwt.sign(new ECDSASigner(ecKey));
        VqpsSubmission succeeded = new VqpsSubmission()
                .id(UUID.randomUUID()).status(VqpsSubmissionStatus.PUBLICATION_SUCCEEDED)
                .publicationResult(new VqpsPublicationResult().jwt(signedJwt.serialize()));
        when(vqpsSubmissionB2BApi.createVqpsSubmission(any())).thenReturn(Mono.just(succeeded));
        when(vqpsRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOrRegisterVqps(buildPurpose(), Map.of("k", "v"), FAR_FUTURE_TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no exp claim");
    }
    private VerificationPurposeDto buildPurpose() {
        return VerificationPurposeDto.builder()
                .scope(SCOPE)
                .purposeName(Map.of("en", "Test Verification", "de-CH", "Testverifikation"))
                .purposeDescription(Map.of("en", "A test.", "de-CH", "Ein Test."))
                .build();
    }
    private void mockTmsImmediateSuccess(String jwt) {
        VqpsSubmission submission = new VqpsSubmission()
                .id(UUID.randomUUID()).status(VqpsSubmissionStatus.PUBLICATION_SUCCEEDED)
                .publicationResult(new VqpsPublicationResult().jwt(jwt)
                        .expiresAt(Date.from(Instant.now().plus(30, ChronoUnit.DAYS))));
        when(vqpsSubmissionB2BApi.createVqpsSubmission(any())).thenReturn(Mono.just(submission));
    }
    private static String buildJwt(Instant expiresAt) {
        try {
            var ecKey = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
            var signed = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("test-key").build(),
                    new JWTClaimsSet.Builder()
                            .jwtID(UUID.randomUUID().toString())
                            .expirationTime(Date.from(expiresAt))
                            .build());
            signed.sign(new ECDSASigner(ecKey));
            return signed.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
