package ch.admin.bj.swiyu.verifier.service.oid4vp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;

import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolver;
import ch.admin.bj.swiyu.verifier.service.trustregistry.TestTrustStatementGenerator;
import ch.admin.bj.swiyu.verifier.service.trustregistry.TrustStatementCacheService;
import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;

/**
 * Unit tests for {@link TrustProtocol2Validator}.
 * The real {@link ch.admin.bj.swiyu.tsverifier.TrustStatementVerifier} is
 * replaced
 * by a test double (see
 * {@code src/test/java/ch/admin/bj/swiyu/tsverifier/TrustStatementVerifier.java})
 * that returns configurable data. This allows us to focus on the orchestration
 * logic
 * inside the validator without needing real JWTs, signatures or network calls.
 */
class TrustProtocol2ValidatorTest {

    @Mock
    private TrustStatementCacheService statementProvider;
    @Mock
    private StatusListResolver statusListResolverAdapter;
    @Mock
    private DidJwtValidator jwtValidator;
    @Mock
    private IssuerPublicKeyLoader keyLoader;

    private TrustProtocol2Validator validator;
    private Management management;
    private TrustAnchor anchor;
    private ECKey mockKey;
    private static final String TRUST_ROOT = "did:webvh:testscid:anchor1";
    private static final String TRUST_ROOT_KID = TRUST_ROOT + "#key-1";
    private static final String ISSUER_DID = "did:webvh:testscid:issuer";
    private static final String TRUSTED_VCT = "urn::trusted:test:vct";
    private TestTrustStatementGenerator trustStatementGenerator;
    /**
     * List of valid trust statements
     */
    private List<SignedJWT> trustStatements;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        validator = new TrustProtocol2Validator(statementProvider);
        anchor = new TrustAnchor(TRUST_ROOT, "https://www.example.com");
        management = Management.builder()
                .trustAnchors(List.of(anchor))
                .build();
        mockKey = new ECKeyGenerator(Curve.P_256).keyID(TRUST_ROOT_KID).generate();
        trustStatementGenerator = new TestTrustStatementGenerator(mockKey);

        when(keyLoader.loadJWK(anyString())).thenReturn(mockKey.toPublicJWK());
        when(statusListResolverAdapter.resolveStatusList(anyString())).then(a -> trustStatementGenerator.generateTokenStatusList(TRUST_ROOT_KID, a.getArgument(0)));

        trustStatements = List.of(
            trustStatementGenerator.generateIdTsJwt(TRUST_ROOT_KID, ISSUER_DID),
            trustStatementGenerator.generateNcTlsJwt(TRUST_ROOT_KID, "someOtherDid"),
            trustStatementGenerator.generatePiTlsJwt(TRUST_ROOT_KID, TRUSTED_VCT),
            trustStatementGenerator.generatePiaTsJwt(TRUST_ROOT_KID, ISSUER_DID, TRUSTED_VCT)
        );
    }

    @Test
    void isTrusted_returnsTrueWhenIssuerMarkersIndicateTrust() throws LoadingPublicKeyOfIssuerFailedException {
        // arrange: stub verifier to return a TrustVerificationResult with a trusted
        // marker
        // Build Valid Trust Statements linked to the status list
        when(statementProvider.getAllIssuanceStatementsFor(anyString()))
                .thenReturn(trustStatements.stream().map(SignedJWT::serialize).toList());

        boolean result = validator.isTrusted(ISSUER_DID, TRUSTED_VCT, management);
        assertThat(result).as("Issuer should be trusted when markers indicate trust").isTrue();
    }
}
