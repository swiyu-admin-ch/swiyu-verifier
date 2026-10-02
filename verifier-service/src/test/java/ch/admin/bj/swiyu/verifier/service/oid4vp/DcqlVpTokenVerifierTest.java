package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwt;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtParser;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtVcValidator;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtParseException;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtVerificationException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.IssuerTrustMarker;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DcqlVpTokenVerifierTest {

    private static final String TEST_ISSUER = "did:webvh:sid:example.com";
    private static final String TEST_VCT = "test-vct";

    @Mock
    private SdJwtVpTokenVerifier sdJwtVpTokenVerifier;

    @Mock
    private IssuerTrustValidator issuerTrustValidator;

    @Mock
    private DidResolverFacade didResolver;

    @Mock
    private DidKidParser didKidParser;

    @Mock
    private SdJwtVcValidator sdJwtVcValidator;

    @InjectMocks
    private DcqlVpTokenVerifier dcqlVpTokenVerifier;

    private AutoCloseable mocks;
    private MockedStatic<SdJwtParser> sdJwtParserStatic;
    private SdJwt vpToken;
    private Management management;
    private String serializedVpToken;

    @BeforeEach
    void setUp() throws SdJwtVerificationException {
        mocks = MockitoAnnotations.openMocks(this);

        vpToken = mock(SdJwt.class);
        management = mock(Management.class);
        serializedVpToken = getDummyJwt().serialize();

        sdJwtParserStatic = mockStatic(SdJwtParser.class);
        sdJwtParserStatic.when(() -> SdJwtParser.parseSdJwt(serializedVpToken)).thenReturn(vpToken);

        when(didKidParser.getDidFromAbsoluteKid(anyString())).thenReturn(TEST_ISSUER);

        when(vpToken.getJwt()).thenReturn(getDummyJwt());
        when(vpToken.getHeader()).thenReturn(getDummyJwt().getHeader());
        when(vpToken.getClaims()).thenReturn(new JWTClaimsSet.Builder()
                .issuer("did:webvh:ignored.example.com")
                .claim("vct", TEST_VCT)
                .build());
        when(didResolver.resolveKey(anyString())).thenReturn(mock(com.nimbusds.jose.jwk.JWK.class));

        // Configure the mock singleton SdJwtVcValidator
        doNothing().when(sdJwtVcValidator).validateAndSetHeader(vpToken);
        doNothing().when(sdJwtVcValidator).validateAndSetJwt(eq(vpToken), any());

        doNothing().when(sdJwtVpTokenVerifier).validateKeyBinding(any(), anyBoolean(), eq(management), any());
        when(sdJwtVpTokenVerifier.verifyStatus(anyMap(), any())).thenReturn(Optional.empty());
        when(issuerTrustValidator.validateTrust(anyString(), anyString(), eq(management)))
                .thenReturn(IssuerTrustMarker.builder().isTrusted(true).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (sdJwtParserStatic != null) {
            sdJwtParserStatic.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void verifyVpTokenForDCQLRequest_withDefaultHolderBindingRequirement_passesTrueToKeyBindingValidation() throws Exception {
        when(vpToken.hasKeyBinding()).thenReturn(true);
        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(null).build();

        var result = dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(serializedVpToken, management, dcqlCredential);

        assertThat(result.sdJwt()).isEqualTo(vpToken);
        verify(sdJwtVpTokenVerifier).validateKeyBinding(eq(vpToken), eq(true), eq(management), eq(sdJwtVcValidator));
        verify(issuerTrustValidator).validateTrust(eq(TEST_ISSUER), eq(TEST_VCT), eq(management));
        verify(sdJwtVcValidator).validateAndSetHeader(vpToken);
    }

    @Test
    void verifyVpTokenForDCQLRequest_withExplicitlyDisabledHolderBinding_passesFalseToKeyBindingValidation() {
        when(vpToken.hasKeyBinding()).thenReturn(false);
        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(false).build();

        var result = dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(serializedVpToken, management, dcqlCredential);

        assertThat(result.sdJwt()).isEqualTo(vpToken);
        verify(sdJwtVpTokenVerifier).validateKeyBinding(eq(vpToken), eq(false), eq(management), eq(sdJwtVcValidator));
    }

    @Test
    void verifyVpTokenForDCQLRequest_whenSdJwtParsingFails_throwsVerificationException() {
        sdJwtParserStatic.when(() -> SdJwtParser.parseSdJwt(serializedVpToken))
                .thenThrow(new SdJwtParseException("parse failure"));

        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(false).build();

        assertThatThrownBy(() -> dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(serializedVpToken, management, dcqlCredential))
                .isInstanceOf(VerificationException.class);
    }

    @Test
    void verifyVpTokenForDCQLRequest_whenHeaderValidationFails_throwsVerificationException() throws SdJwtVerificationException {
        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(false).build();

        // Configure the singleton validator to throw on header validation
        doThrow(new SdJwtVerificationException("bad header")).when(sdJwtVcValidator).validateAndSetHeader(vpToken);

        assertThatThrownBy(() -> dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(serializedVpToken, management, dcqlCredential))
                .isInstanceOf(VerificationException.class);
    }

    private SignedJWT getDummyJwt() {
        var key = assertDoesNotThrow(() -> new ECKeyGenerator(Curve.P_256)
                .keyID("key-1")
                .algorithm(JWSAlgorithm.ES256)
                .generate());
        var jwt = new SignedJWT(
                new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(TEST_ISSUER + "#" + key.getKeyID())
                        .build(),
                new JWTClaimsSet.Builder()
                        .jwtID("1234")
                        .issuer("did:webvh:other.example.com")
                        .build()
        );
        assertDoesNotThrow(() -> jwt.sign(new ECDSASigner(key)));
        return jwt;
    }
}
