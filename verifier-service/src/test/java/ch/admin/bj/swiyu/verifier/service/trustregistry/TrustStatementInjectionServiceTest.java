package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.dto.requestobject.RequestObjectDto;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TrustStatementInjectionService}.
 *
 * <p>Verifies that the correct subset of {@code pvaTS} JWTs is selected and injected
 * based on the {@code authorized_fields} claim and the requested DCQL claim paths.</p>
 */
class TrustStatementInjectionServiceTest {

    private static final String VERIFIER_DID = "did:tdw:example.com:verifier";
    private static final byte[] HMAC_SECRET = new byte[32];

    private TrustStatementCacheService cacheService;
    private TrustStatementValidator validator;
    private ApplicationProperties applicationProperties;
    private TrustStatementInjectionService injectionService;

    @BeforeEach
    void setUp() {
        cacheService = mock(TrustStatementCacheService.class);
        validator = mock(TrustStatementValidator.class);
        applicationProperties = mock(ApplicationProperties.class);
        when(applicationProperties.getClientId()).thenReturn(VERIFIER_DID);
        // no-op signatures by default
        doNothing().when(validator).validateSignature(anyString());

        injectionService = new TrustStatementInjectionService(cacheService, applicationProperties, validator);
    }

    // --- pvaTS selection tests ---

    @Test
    void injectVerifierInfo_withMultiplePvaTs_injectsOnlyMatchingOnes() throws Exception {
        String pvaTsPAN = buildJwt(List.of("personal_administrative_number"), Instant.now().plusSeconds(3600));
        String pvaTsBirth = buildJwt(List.of("birth_date"), Instant.now().plusSeconds(3600));
        String pvaTsAddress = buildJwt(List.of("address"), Instant.now().plusSeconds(3600));

        String idTs = buildJwt(List.of(), Instant.now().plusSeconds(3600));

        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(idTs);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID))
                .thenReturn(List.of(pvaTsPAN, pvaTsBirth, pvaTsAddress));

        Management management = buildManagement(List.of("personal_administrative_number", "birth_date"));
        RequestObjectDto base = RequestObjectDto.builder().build();

        RequestObjectDto result = injectionService.injectVerifierInfo(base, management);

        assertThat(result.getVerifierInfo()).hasSize(3) // idTS + pvaTsPAN + pvaTsBirth
                .anySatisfy(entry -> assertThat(entry.getData()).isEqualTo(idTs))
                .anySatisfy(entry -> assertThat(entry.getData()).isEqualTo(pvaTsPAN))
                .anySatisfy(entry -> assertThat(entry.getData()).isEqualTo(pvaTsBirth));

        assertThat(result.getVerifierInfo())
                .noneMatch(entry -> entry.getData().equals(pvaTsAddress));
    }

    @Test
    void injectVerifierInfo_withNoPvaTs_returnsRequestObjectWithOnlyIdTs() throws Exception {
        String idTs = buildJwt(List.of(), Instant.now().plusSeconds(3600));
        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(idTs);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID)).thenReturn(List.of());

        Management management = buildManagement(List.of("birth_date"));
        RequestObjectDto result = injectionService.injectVerifierInfo(RequestObjectDto.builder().build(), management);

        assertThat(result.getVerifierInfo()).hasSize(1);
        assertThat(result.getVerifierInfo().getFirst().getData()).isEqualTo(idTs);
    }

    @Test
    void injectVerifierInfo_whenPvaTsSignatureInvalid_thenSkippedAndCacheInvalidated() throws Exception {
        String pvaTsPAN = buildJwt(List.of("personal_administrative_number"), Instant.now().plusSeconds(3600));
        String idTs = buildJwt(List.of(), Instant.now().plusSeconds(3600));

        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(idTs);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID))
                .thenReturn(List.of(pvaTsPAN));

        // idTS signature ok, pvaTS fails
        doNothing().when(validator).validateSignature(eq(idTs));
        doThrow(new JwtValidatorException("bad sig")).when(validator).validateSignature(eq(pvaTsPAN));

        Management management = buildManagement(List.of("personal_administrative_number"));
        RequestObjectDto result = injectionService.injectVerifierInfo(RequestObjectDto.builder().build(), management);

        // idTS injected, pvaTS skipped
        assertThat(result.getVerifierInfo()).hasSize(1);
        assertThat(result.getVerifierInfo().getFirst().getData()).isEqualTo(idTs);
        verify(cacheService).invalidateAllTrustStatements(VERIFIER_DID);
    }

    @Test
    void injectVerifierInfo_whenIdTsSignatureInvalid_thenRequestObjectReturnedWithoutVerifierInfo() throws Exception {
        String idTs = buildJwt(List.of(), Instant.now().plusSeconds(3600));
        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(idTs);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID)).thenReturn(List.of());
        doThrow(new JwtValidatorException("bad sig")).when(validator).validateSignature(eq(idTs));

        Management management = buildManagement(List.of("birth_date"));
        RequestObjectDto base = RequestObjectDto.builder().build();
        RequestObjectDto result = injectionService.injectVerifierInfo(base, management);

        assertThat(result.getVerifierInfo()).isNull();
        verify(cacheService).invalidateAllTrustStatements(VERIFIER_DID);
    }

    @Test
    void injectVerifierInfo_whenPvaTsHasNoAuthorizedFields_thenAlwaysInjected() throws Exception {
        // pvaTS without authorized_fields should be included unconditionally
        String pvaTsNoFields = buildJwtNoAuthorizedFields(Instant.now().plusSeconds(3600));
        String idTs = buildJwt(List.of(), Instant.now().plusSeconds(3600));

        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(idTs);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID))
                .thenReturn(List.of(pvaTsNoFields));

        Management management = buildManagement(List.of("birth_date"));
        RequestObjectDto result = injectionService.injectVerifierInfo(RequestObjectDto.builder().build(), management);

        assertThat(result.getVerifierInfo()).hasSize(2);
    }

    @Test
    void injectVerifierInfo_whenNoCacheAvailable_thenOriginalRequestObjectReturned() {
        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(null);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID)).thenReturn(List.of());

        Management management = buildManagement(List.of("birth_date"));
        RequestObjectDto base = RequestObjectDto.builder().clientId("test").build();
        RequestObjectDto result = injectionService.injectVerifierInfo(base, management);

        assertThat(result).isSameAs(base);
    }

    // --- helpers ---

    private static Management buildManagement(List<String> requestedFields) {
        List<DcqlClaim> claims = requestedFields.stream()
                .map(f -> DcqlClaim.builder().path(List.of(f)).build())
                .toList();
        DcqlCredential credential = DcqlCredential.builder()
                .id("cred1")
                .format("dc+sd-jwt")
                .meta(DcqlCredentialMeta.builder().build())
                .claims(claims)
                .build();
        DcqlQuery query = DcqlQuery.builder().credentials(List.of(credential)).build();

        return Management.builder()
                .dcqlQuery(query)
                .configurationOverride(ConfigurationOverride.builder().build())
                .build();
    }

    private static String buildJwt(List<String> authorizedFields, Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("authorized_fields", authorizedFields)
                .expirationTime(Date.from(exp))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_SECRET));
        return jwt.serialize();
    }

    private static String buildJwtNoAuthorizedFields(Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .expirationTime(Date.from(exp))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_SECRET));
        return jwt.serialize();
    }
}

