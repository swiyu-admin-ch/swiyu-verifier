package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.tsbuilder.IdTsBuilder;
import ch.admin.bj.swiyu.tsbuilder.PvaTsBuilder;
import ch.admin.bj.swiyu.verifier.PostgreSQLContainerInitializer;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.ManagementRepository;
import ch.admin.bj.swiyu.verifier.domain.management.VerificationStatus;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlQuery;
import ch.admin.bj.swiyu.verifier.service.trustregistry.TrustStatementCacheService;
import ch.admin.bj.swiyu.verifier.service.trustregistry.TrustStatementValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Happy-path integration test verifying that Trust Protocol 2.0 trust statements
 * ({@code idTS} and {@code pvaTS}) are successfully injected into the OID4VP
 * {@code GET /oid4vp/api/request-object/{request_id}} response.
 *
 * <p>What is tested: the full Spring context is started, a real {@link Management} entity is
 * persisted in the PostgreSQL test container, and {@code GET request-object/{id}} is exercised
 * via MockMvc. The response body must contain a {@code verifier_info} array holding at least
 * one {@code idTS} and one {@code pvaTS} entry in {@code {"format":"jwt","data":"..."}} form.</p>
 *
 * <p>Boundary conditions:</p>
 * <ul>
 *   <li>The {@link TrustStatementCacheService} is replaced by a Mockito stub so that
 *       no real Trust Registry network call is made.</li>
 *   <li>The {@link TrustStatementValidator} is replaced by a no-op stub so that
 *       DID-document resolution and signature verification are bypassed.</li>
 *   <li>The Management entity's DCQL query requests {@code last_name} and {@code first_name}.
 *       The stubbed {@code pvaTS} JWT authorizes both fields so that field-matching selects it.</li>
 *   <li>The Management entity uses {@code jwtSecuredAuthorizationRequest = false} so that
 *       the response is plain JSON (not a signed JWT string), which can be inspected directly.</li>
 * </ul>
 *
 * <p>Expected result: HTTP 200 with a JSON body where
 * {@code verifier_info[*].data} contains two non-blank JWT strings.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "swiyu.trust-registry.api-url=https://trust-registry.example.ch",
                "swiyu.trust-registry.customer-key=test-key",
                "swiyu.trust-registry.customer-secret=test-secret",
                "swiyu.trust-registry.max-cache-size=100",
                "swiyu.trust-registry.clock-skew-buffer-seconds=0",
                "swiyu.trust-registry.max-cache-ttl-seconds=3600",
                "swiyu.trust-registry.negative-cache-ttl-seconds=30"
        }
)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgreSQLContainerInitializer.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Slf4j
class VerificationControllerTrustStatementIT {

    /**
     * A well-formed {@code kid} matching the Trust Registry DID pattern required by
     * the {@code swiyu-ts-builder} validation (did:tdw / did:webvh with SCID + key fragment).
     */
    private static final String TRUST_REGISTRY_KID =
            "did:tdw:QmYyQSo1c1Ym7orWxLYvCrzRLZad5ZxQ8HkBLyEE4RRAA1:identifier.admin.ch:api:v1:did#assert-key-01";

    /**
     * The issuer DID as configured in the test {@code application.yml}
     * ({@code application.issuer-id=did:tdw:example}).
     */
    private static final String VERIFIER_DID = "did:example:12345";

    /**
     * A syntactically valid subject DID used as the {@code sub} claim of the Trust Statements.
     */
    private static final String VERIFIER_SUBJECT_DID =
            "did:tdw:QmYyQSo1c1Ym7orWxLYvCrzRLZad5ZxQ8HkBLyEE4RRBB1:identifier.admin.ch:api:v1:did";

    private static final UUID REQUEST_ID = UUID.fromString("aabbccdd-aabb-aabb-aabb-aabbccdd0001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ManagementRepository managementRepository;

    /**
     * Replaces the real Caffeine-backed cache so that trust statements are returned
     * directly without any Trust Registry network call.
     */
    @MockitoBean
    private TrustStatementCacheService trustStatementCacheService;

    /**
     * Replaces the DID-JWT validator so that signature verification always succeeds
     * without resolving a DID Document from the network.
     */
    @MockitoBean
    private TrustStatementValidator trustStatementValidator;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        // DCQL query requesting last_name and first_name – both covered by the pvaTS stub below
        String dcqlJson = """
                {
                  "credentials": [{
                    "id": "cred1",
                    "format": "dc+sd-jwt",
                    "meta": { "vct_values": ["https://example.com/vct/v1"] },
                    "claims": [
                      {"path": ["last_name"]},
                      {"path": ["first_name"]}
                    ]
                  }]
                }
                """;

        managementRepository.save(Management.builder()
                .id(REQUEST_ID)
                .state(VerificationStatus.PENDING)
                .jwtSecuredAuthorizationRequest(true)
                .expirationInSeconds(86400)
                .expiresAt(4070908800000L)
                .acceptedIssuerDids(List.of("TEST_ISSUER_ID"))
                .dcqlQuery(objectMapper.readValue(dcqlJson, DcqlQuery.class))
                .build());
    }

    @AfterEach
    void cleanup() {
        managementRepository.deleteById(REQUEST_ID);
    }

    /**
     * Verifies the happy path: both {@code idTS} and {@code pvaTS} are present in
     * {@code verifier_info} of the signed JWT-Secured Authorization Request.
     *
     * <p>The response is a compact-serialized signed JWT (Content-Type:
     * {@code application/oauth-authz-req+jwt}). The JWT payload is parsed without
     * signature verification; the {@code verifier_info} claim must contain exactly
     * two entries in {@code {"format":"jwt","data":"..."}} form.</p>
     *
     * @throws Exception if MockMvc or JWT operations fail
     */
    @Test
    void givenIdTsAndPvaTs_whenGetRequestObject_thenVerifierInfoContainsBothTrustStatements()
            throws Exception {

        // --- arrange: build JWTs with ephemeral key ---
        ECKey ecKey = new ECKeyGenerator(Curve.P_256).generate();
        ECDSASigner signer = new ECDSASigner(ecKey);

        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.DAYS);

        SignedJWT idTs = new IdTsBuilder()
                .withKid(TRUST_REGISTRY_KID)
                .withSubject(VERIFIER_SUBJECT_DID)
                .withValidity(now, expiry)
                .withStatus(1, "https://status.example.ch/list/1")
                .addEntityName("Test Issuer AG")
                .withIsStateActor(false)
                .addRegistryId("UID", "CHE-000.000.000")
                .build();
        idTs.sign(signer);

        SignedJWT pvaTs = new PvaTsBuilder()
                .withKid(TRUST_REGISTRY_KID)
                .withSubject(VERIFIER_SUBJECT_DID)
                .withValidity(Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS))
                .withStatus(7, "https://status.example.ch/list/1")
                .withJti("550e8400-e29b-41d4-a716-446655440000")
                .withAuthorizedFields(List.of("last_name", "first_name"))
                .build();
        pvaTs.sign(signer);

        String idTsJwt = idTs.serialize();
        String pvaTsJwt = pvaTs.serialize();

        // --- arrange: stub cache & validator ---
        when(trustStatementCacheService.getIdentityTrustStatement(VERIFIER_DID)).thenReturn(idTsJwt);
        when(trustStatementCacheService.getProtectedVerificationAuthorizationTrustStatements(VERIFIER_DID))
                .thenReturn(List.of(pvaTsJwt));
        doNothing().when(trustStatementValidator).validateSignature(anyString());

        // --- act ---
        String responseJwtString = mockMvc.perform(get("/oid4vp/api/request-object/" + REQUEST_ID)
                        .accept("application/oauth-authz-req+jwt"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // --- assert: parse JWT payload without signature verification ---
        SignedJWT responseJwt = SignedJWT.parse(responseJwtString);
        List<Map<String, Object>> verifierInfo = (List<Map<String, Object>>)
                responseJwt.getJWTClaimsSet().getClaim("verifier_info");

        log.info("[DEMO] Full JWT payload:\n{}", responseJwt.getJWTClaimsSet().toJSONObject());
        log.info("[DEMO] verifier_info: {}", verifierInfo);

        assertThat(verifierInfo)
                .isNotNull()
                .hasSize(2)
                .allSatisfy(entry -> {
                    assertThat(entry.get("format")).isEqualTo("jwt");
                    assertThat((String) entry.get("data")).isNotBlank();
                });
    }

    // --- helper ---

    private static String buildJwt(ECDSASigner signer, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }
}
