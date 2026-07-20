package ch.admin.bj.swiyu.verifier.service.trustregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.dto.StatusVerificationResultDto;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListCacheService;

public class TrustStatementValidatorTest {

    private DidJwtValidator trustStatementDidJwtValidator;
    private TrustRegistryProperties trustRegistryProperties;
    private StatusListCacheService statusListCacheService;
    private IssuerPublicKeyLoader keyLoader;
    private TokenStatusListVerifier statusListVerifier;
    private TokenStatusListTokenDto statusListTokenDto;

    private TrustStatementValidator validator;
    private ECKey testKey;
    private String testJwt;
    private long expiry;
    private static final int DEFAULT_VALIDITY_SECONDS = 3000;
    private static final String ISS = "did:example";
    private static final String KID = "did:example#key-1";
    private static final String STATUS_LIST_URI = "https://www.example.com/statuslist/1";

    @BeforeEach
    void setUp() throws JOSEException, LoadingPublicKeyOfIssuerFailedException, IOException {
        testKey = new ECKeyGenerator(Curve.P_256).algorithm(JWSAlgorithm.ES256).keyID(KID).keyUse(KeyUse.SIGNATURE).generate();
        trustStatementDidJwtValidator = mock(DidJwtValidator.class);
        trustRegistryProperties = mock(TrustRegistryProperties.class);
        statusListCacheService = mock(StatusListCacheService.class);
        keyLoader = mock(IssuerPublicKeyLoader.class);
        statusListVerifier = mock(TokenStatusListVerifier.class);
        validator = new TrustStatementValidator(
                trustStatementDidJwtValidator,
                trustRegistryProperties,
                statusListCacheService,
                keyLoader,
                statusListVerifier);
        when(trustStatementDidJwtValidator.getAndValidateResolutionUrl(anyString())).thenReturn("TEST");
        when(trustStatementDidJwtValidator.getDidString(anyString())).thenReturn("TEST");
        when(keyLoader.loadJWK(eq(ISS), eq(KID))).thenReturn(testKey.toPublicJWK());
        statusListTokenDto = mock(TokenStatusListTokenDto.class);
        when(statusListCacheService.getTokenStatusListTokenByUri(eq(STATUS_LIST_URI))).thenReturn(statusListTokenDto);
        expiry = Instant.now().plusSeconds(DEFAULT_VALIDITY_SECONDS).getEpochSecond();
        when(statusListTokenDto.getExp()).thenReturn(expiry+100); // Adding some seconds here to use by default expiry form token status list 
        when(statusListTokenDto.getTtl()).thenReturn(DEFAULT_VALIDITY_SECONDS);
        var verificationResult = mock(StatusVerificationResultDto.class);
        when(statusListVerifier.verifyStatus(any(), any())).thenReturn(verificationResult);
        when(verificationResult.valid()).thenReturn(true);

        when(trustRegistryProperties.getMaxCacheTtlSeconds()).thenReturn(Long.valueOf(DEFAULT_VALIDITY_SECONDS));
        testJwt = buildExpiringJWT(expiry);
    }


    @Test
    void testTrustStatementValidityWindow_whenValid_minimumGivenFromExpiry() {
        var validity = assertDoesNotThrow(() -> validator.trustStatementValidityWindow(testJwt));
        assertThat(validity.isValid()).isTrue();
        assertThat(validity.valditiyWindow())
                .isGreaterThanOrEqualTo(
                        TimeUnit.SECONDS.toNanos(expiry) - TimeUnit.MILLISECONDS.toNanos(Instant.now().toEpochMilli()))
                .isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(DEFAULT_VALIDITY_SECONDS));
    }

    @Test
    void testTrustStatementValidityWindow_whenValid_minimumGivenFromStatusListTTL() {
        int ttl = 2; // 2 Seconds
        when(statusListTokenDto.getTtl()).thenReturn(ttl);
        var validity = assertDoesNotThrow(() -> validator.trustStatementValidityWindow(testJwt));
        assertThat(validity.isValid()).isTrue();
        assertThat(validity.valditiyWindow()).isGreaterThan(0).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(ttl));
    }

    @Test
    void testTrustStatementValidityWindow_whenValid_minimumGivenFromStatusListExpiry() {
        int closerExpirySeconds = 2;
        var closerExpiry = Instant.now().plusSeconds(closerExpirySeconds).getEpochSecond();
        when(statusListTokenDto.getExp()).thenReturn(closerExpiry);
        var validity = assertDoesNotThrow(() -> validator.trustStatementValidityWindow(testJwt));
        assertThat(validity.isValid()).isTrue();
        assertThat(validity.valditiyWindow())
                .isGreaterThanOrEqualTo(
                        TimeUnit.SECONDS.toNanos(closerExpiry) - TimeUnit.MILLISECONDS.toNanos(Instant.now().toEpochMilli()))
                .isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(closerExpirySeconds));
    }

    @Test
    void testTrustStatementValidityWindow_whenValid_minimumGivenFromMaxCacheTTLConfig(){
        int ttl = 2;
        when(trustRegistryProperties.getMaxCacheTtlSeconds()).thenReturn(Long.valueOf(ttl));
        var validity = assertDoesNotThrow(() -> validator.trustStatementValidityWindow(testJwt));
        assertThat(validity.isValid()).isTrue();
        assertThat(validity.valditiyWindow()).isGreaterThan(0).isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(ttl));
    }

    private String buildExpiringJWT(long expiry) {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(testKey.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                    .issuer(ISS)
                    .claim("exp", expiry)
                    .claim("status", Map.of(
                        "status_list", Map.of(
                            "idx", 1,
                            "uri", STATUS_LIST_URI
                        )
                    ))
                    .build());
        assertDoesNotThrow(() -> jwt.sign(new ECDSASigner(testKey)));
        return jwt.serialize();
    }

    /**
     * Create a Mock Token Status List with partial info
     * @param ttl
     * @param expEpochSeconds
     * @return
     */
    private TokenStatusListTokenDto getTokenStatusListTokenDto(int ttl, long expEpochSeconds) {
        var token = mock(TokenStatusListTokenDto.class);
        when(token.getExp()).thenReturn(expEpochSeconds);
        token.setTtl(ttl);
        token.setSub(STATUS_LIST_URI);
        return token;

    }
}
