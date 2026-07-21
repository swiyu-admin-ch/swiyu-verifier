package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.core.trust.client.api.TrustProtocol20Api;
import ch.admin.bj.swiyu.core.trust.client.model.PagedModelString;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.service.trustregistry.TrustStatementValidator.TrustStatementValidationResult;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TrustStatementCacheService}, focusing on the 1:N pvaTS fetch,
 * caching, and TTL computation behaviour.
 */
class TrustStatementCacheServiceTest {

    private static final String VERIFIER_DID = "did:tdw:example.com:verifier";
    private static final byte[] HMAC_SECRET = new byte[32]; // all-zeros, sufficient for test JWTs

    private TrustProtocol20Api trustProtocol20Api;
    private TrustRegistryProperties properties;
    private CacheMaintenanceService cacheMaintenanceService;
    private TrustStatementCacheService cacheService;
    private TrustStatementValidator statementValidator;
    private CacheProperties cacheProperties;

    @BeforeEach
    void setUp() {
        trustProtocol20Api = mock(TrustProtocol20Api.class);
        cacheMaintenanceService = mock(CacheMaintenanceService.class);
        cacheProperties = new CacheProperties();
        cacheProperties.setRequestBackoffSeconds(10);
        cacheProperties.setStatusListCacheTtl(100l);
        statementValidator = mock(TrustStatementValidator.class);
        properties = new TrustRegistryProperties();
        properties.setMaxCacheSize(100);
        properties.setClockSkewBufferSeconds(0);
        properties.setMaxCacheTtlSeconds(0); // no cap in tests

        cacheService = new TrustStatementCacheService(
                trustProtocol20Api,
                properties,
                cacheMaintenanceService,
                statementValidator,
                cacheProperties
        );
        
        // By Default accept trust statements in these tests with 5 minutes TTL to allow debugging
        when(statementValidator.trustStatementValidityWindow(any())).thenReturn(new TrustStatementValidationResult(true, TimeUnit.MINUTES.toNanos(5)));
    }


    @Test
    void getIdentityTrustStatement_firstCall_fetchesFromApi() throws Exception {
        String jwt = buildJwt(List.of("birth_date"), Instant.now().plusSeconds(3600));
        when(trustProtocol20Api.getIdTS(eq(VERIFIER_DID)))
                .thenReturn(Mono.just(jwt));
        when(statementValidator.trustStatementValidityWindow(eq(jwt))).thenReturn(new TrustStatementValidationResult(true, 5l));
        var result = cacheService.getIdentityTrustStatement(VERIFIER_DID);

        assertThat(result).isEqualTo(jwt);
        verify(trustProtocol20Api, times(1)).getIdTS(any());
    }

    @Test
    void getIdentityTrustStatement_secondCall_usesCache() throws Exception {
        String jwt = buildJwt(List.of("birth_date"), Instant.now().plusSeconds(3600));
        when(trustProtocol20Api.getIdTS(any()))
                .thenReturn(Mono.just(jwt));
        when(statementValidator.trustStatementValidityWindow(eq(jwt)))
            .thenReturn(new TrustStatementValidationResult(true, TimeUnit.SECONDS.toNanos(5l)));

        cacheService.getIdentityTrustStatement(VERIFIER_DID);
        cacheService.getIdentityTrustStatement(VERIFIER_DID);

        // API must only be called once – second call hits the cache
        verify(trustProtocol20Api, times(1)).getIdTS(any());
    }

    @Test
    void getIdentityTrustStatement_secondCallWhen503_cacheIsNotUsed() {
        var numberOfCalls = 2;
        when(trustProtocol20Api.getIdTS(any()))
                .thenReturn(Mono.error(WebClientResponseException.create(503, "Service Unavailable", null, null, null)));

        for (int i = 0; i < numberOfCalls; i++) {
            cacheService.getIdentityTrustStatement(VERIFIER_DID);
        }

        // API must only be called once – second call hits the cache
        verify(trustProtocol20Api, times(numberOfCalls)).getIdTS(any());
    }

    @Test
    void getProtectedVerificationAuthorizationTrustStatements_whenApiReturnsMultipleJwts_thenAllAreCached() throws Exception {
        String jwt1 = buildJwt(List.of("personal_administrative_number"), Instant.now().plusSeconds(3600));
        String jwt2 = buildJwt(List.of("birth_date"), Instant.now().plusSeconds(3600));

        var page = mock(PagedModelString.class);
        when(page.getContent()).thenReturn(List.of(jwt1, jwt2));
        when(trustProtocol20Api.listPvaTS(eq(VERIFIER_DID), eq(true), any(), any(), any()))
                .thenReturn(Mono.just(page));

        List<String> result = cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID);

        assertThat(result).hasSize(2).containsExactlyInAnyOrder(jwt1, jwt2);
        assertThat(cacheService.getPvaTsCache().estimatedSize())
            .as("The two statements are returned for the same did, only 1 entry should be made for the did")
            .isEqualTo(1);
    }

    @Test
    void getProtectedVerificationAuthorizationTrustStatements_whenApiReturnsEmpty_thenEmptyListReturned() {
        var page = mock(PagedModelString.class);
        when(page.getContent()).thenReturn(List.of());
        when(trustProtocol20Api.listPvaTS(eq(VERIFIER_DID), eq(true), any(), any(), any()))
                .thenReturn(Mono.just(page));

        List<String> result = cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID);

        assertThat(result).isEmpty();
        assertThat(cacheService.getPvaTsCache().estimatedSize())
            .as("When no valid trust statement is found, but a return value is saved should be cached")
            .isEqualTo(1);
    }

    @Test
    void getProtectedVerificationAuthorizationTrustStatements_whenApiThrows_thenEmptyListReturned() {
        when(trustProtocol20Api.listPvaTS(any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("connection refused")));

        List<String> result = cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID);

        assertThat(result).isEmpty();
        assertThat(cacheService.getPvaTsCache().estimatedSize())
            .as("When API error is detected, should not be cached")
            .isEqualTo(0);
    }

    @Test
    void getProtectedVerificationAuthorizationTrustStatements_whenCalledTwice_thenApiCalledOnce() throws Exception {
        String jwt = buildJwt(List.of("birth_date"), Instant.now().plusSeconds(3600));
        var page = mock(PagedModelString.class);
        when(page.getContent()).thenReturn(List.of(jwt));
        when(trustProtocol20Api.listPvaTS(eq(VERIFIER_DID), eq(true), any(), any(), any()))
                .thenReturn(Mono.just(page));

        cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID);
        cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID);

        verify(trustProtocol20Api, times(1)).listPvaTS(eq(VERIFIER_DID), eq(true), any(), any(), any());
    }

    @Test
    void invalidateAllTrustStatements_whenCalled_thenSubsequentFetchHitsApi() throws Exception {
        String jwt = buildJwt(List.of("birth_date"), Instant.now().plusSeconds(3600));
        var page = mock(PagedModelString.class);
        when(page.getContent()).thenReturn(List.of(jwt));
        when(trustProtocol20Api.listPvaTS(eq(VERIFIER_DID), eq(true), any(), any(), any()))
                .thenReturn(Mono.just(page));

        cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID);
        assertThat(cacheService.getPvaTsCache().estimatedSize()).as("Cache should have 1 entry").isEqualTo(1);
        cacheService.invalidateAllTrustStatements(VERIFIER_DID);
        assertThat(cacheService.getPvaTsCache().estimatedSize()).as("Cache should be emptied").isEqualTo(0);
        cacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID);
        assertThat(cacheService.getPvaTsCache().estimatedSize()).as("Cache should have again 1 entry").isEqualTo(1);

        verify(trustProtocol20Api, times(2)).listPvaTS(eq(VERIFIER_DID), eq(true), any(), any(), any());
    }

    /**
     * Verifies that {@link TrustStatementCacheService#getAllIssuanceStatementsFor(String)}
     * correctly aggregates the results of the four Trust Registry calls:
     *
     * <p>The test mocks each API call to return a known value, invokes the service method,
     * and asserts that the returned list contains exactly those five JWT strings (order is not
     * important). It also verifies that each API method is invoked exactly once, ensuring that
     * the service does not perform extra network calls.</p>
     *
     * @throws Exception if JWT building or mocking fails (should not happen in this test)
     */
   @Test
    void getAllIssuanceStatementsFor_whenAllProvided_thenSuccess() {
        // --- arrange ---------------------------------------------------------
        String idTsJwt   = "idTS-jwt";
        String piTlsJwt  = "piTLS-jwt";
        String ncTlsJwt  = "ncTLS-jwt";

        // mock the paged response for the PIA‑TS endpoint
        PagedModelString piaTsPage = mock(PagedModelString.class);
        when(piaTsPage.getContent()).thenReturn(List.of("piaTS-1", "piaTS-2"));

        // stub the four API calls used by getAllIssuanceStatementsFor
        when(trustProtocol20Api.getIdTS(anyString())).thenReturn(Mono.just(idTsJwt));
        when(trustProtocol20Api.getActivePiTLS()).thenReturn(Mono.just(piTlsJwt));
        when(trustProtocol20Api.getActiveNcTLS()).thenReturn(Mono.just(ncTlsJwt));
        when(trustProtocol20Api.listPiaTS(eq(VERIFIER_DID), eq(true), any(), any(), any()))
                .thenReturn(Mono.just(piaTsPage));

        // --- act -------------------------------------------------------------
        List<String> statements = cacheService.getAllIssuanceStatementsFor(VERIFIER_DID);

        // --- assert ----------------------------------------------------------
        // we expect the three single‑value JWTs plus the two PIA‑TS entries
        assertThat(statements)
                .hasSize(5)
                .containsExactlyInAnyOrder(idTsJwt, piTlsJwt, ncTlsJwt, "piaTS-1", "piaTS-2");

        // verify that each API method has been called exactly once
        verify(trustProtocol20Api, times(1)).getIdTS(eq(VERIFIER_DID));
        verify(trustProtocol20Api, times(1)).getActivePiTLS();
        verify(trustProtocol20Api, times(1)).getActiveNcTLS();
        verify(trustProtocol20Api, times(1)).listPiaTS(eq(VERIFIER_DID), eq(true), any(), any(), any());
    }
    // --- helpers ---

    /**
     * Builds a minimal HMAC-signed JWT containing an {@code authorized_fields} claim and the
     * given {@code exp}. The signature is irrelevant for unit tests that do not perform
     * cryptographic verification.
     */
    private static String buildJwt(List<String> authorizedFields, Instant exp) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("authorized_fields", authorizedFields)
                .expirationTime(Date.from(exp))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_SECRET));
        return jwt.serialize();
    }
}




