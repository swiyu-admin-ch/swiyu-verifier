package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
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
    private TrustStatementInjectionService injectionService;

    @BeforeEach
    void setUp() {
        cacheService = mock(TrustStatementCacheService.class);
        validator = mock(TrustStatementValidator.class);
        // no-op signatures by default
        doNothing().when(validator).validateSignature(anyString());

        injectionService = new TrustStatementInjectionService(cacheService, validator, null);
    }

    // --- pvaTS selection tests ---

    @Test
    void injectVerifierInfo_withMultiplePvaTs_injectsAllValidOnes() throws Exception {
        String pvaTsPAN = buildJwt(List.of("personal_administrative_number"), Instant.now().plusSeconds(3600));
        String pvaTsBirth = buildJwt(List.of("birth_date"), Instant.now().plusSeconds(3600));
        String pvaTsAddress = buildJwt(List.of("address"), Instant.now().plusSeconds(3600));

        String idTs = buildJwt(List.of(), Instant.now().plusSeconds(3600));

        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(idTs);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID))
                .thenReturn(List.of(pvaTsPAN, pvaTsBirth, pvaTsAddress));

        RequestObjectDto base = RequestObjectDto.builder().build();

        RequestObjectDto result = injectionService.injectVerifierInfo(base, VERIFIER_DID);

        assertThat(result.getVerifierInfo()).hasSize(4) // idTS + all 3 pvaTS
                .anySatisfy(entry -> assertThat(entry.getData()).isEqualTo(idTs))
                .anySatisfy(entry -> assertThat(entry.getData()).isEqualTo(pvaTsPAN))
                .anySatisfy(entry -> assertThat(entry.getData()).isEqualTo(pvaTsBirth))
                .anySatisfy(entry -> assertThat(entry.getData()).isEqualTo(pvaTsAddress));
    }

    @Test
    void injectVerifierInfo_withNoPvaTs_returnsRequestObjectWithOnlyIdTs() throws Exception {
        String idTs = buildJwt(List.of(), Instant.now().plusSeconds(3600));
        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(idTs);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID)).thenReturn(List.of());

        RequestObjectDto result = injectionService.injectVerifierInfo(RequestObjectDto.builder().build(), VERIFIER_DID);

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

        RequestObjectDto result = injectionService.injectVerifierInfo(RequestObjectDto.builder().build(), VERIFIER_DID);

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

        RequestObjectDto base = RequestObjectDto.builder().build();
        RequestObjectDto result = injectionService.injectVerifierInfo(base, VERIFIER_DID);

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

        RequestObjectDto result = injectionService.injectVerifierInfo(RequestObjectDto.builder().build(), VERIFIER_DID);

        assertThat(result.getVerifierInfo()).hasSize(2);
    }

    @Test
    void injectVerifierInfo_whenNoCacheAvailable_thenOriginalRequestObjectReturned() {
        when(cacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(null);
        when(cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID)).thenReturn(List.of());

        RequestObjectDto base = RequestObjectDto.builder().clientId("test").build();
        RequestObjectDto result = injectionService.injectVerifierInfo(base, VERIFIER_DID);

        assertThat(result).isSameAs(base);
    }

    // --- helpers ---


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

