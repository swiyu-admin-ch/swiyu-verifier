package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
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
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DcqlVpTokenVerifierTest {

    private static final String TEST_ISSUER = "did:webvh:sid:example.com";
    private static final String TEST_VCT = "test-vct";

    @Mock
    private SdJwtVpTokenVerifier sdJwtVpTokenVerifier;

    @Mock
    private IssuerTrustValidator issuerTrustValidator;

    @Mock
    private DidResolverFacade didResolver;

    @InjectMocks
    private DcqlVpTokenVerifier dcqlVpTokenVerifier;

    private AutoCloseable mocks;
    private MockedStatic<SdJwtParser> sdJwtParserStatic;
    private SdJwt vpToken;
    private Management management;
    private String serializedVpToken;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        vpToken = mock(SdJwt.class);
        management = mock(Management.class);
        serializedVpToken = getDummyJwt().serialize();

        sdJwtParserStatic = mockStatic(SdJwtParser.class);
        sdJwtParserStatic.when(() -> SdJwtParser.parseSdJwt(serializedVpToken)).thenReturn(vpToken);

        when(vpToken.getJwt()).thenReturn(getDummyJwt());
        when(vpToken.getHeader()).thenReturn(getDummyJwt().getHeader());
        when(vpToken.getClaims()).thenReturn(new JWTClaimsSet.Builder()
                .issuer("did:webvh:ignored.example.com")
                .claim("vct", TEST_VCT)
                .build());
        when(didResolver.resolveKey(anyString())).thenReturn(mock(com.nimbusds.jose.jwk.JWK.class));
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

        try (MockedConstruction<SdJwtVcValidator> validatorConstruction = successfulValidatorConstruction()) {
            var result = dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(serializedVpToken, management, dcqlCredential);

            assertThat(result.sdJwt()).isEqualTo(vpToken);
            verify(sdJwtVpTokenVerifier).validateKeyBinding(eq(vpToken), eq(true), eq(management), any(SdJwtVcValidator.class));
            verify(issuerTrustValidator).validateTrust(eq(TEST_ISSUER), eq(TEST_VCT), eq(management));
            assertThat(validatorConstruction.constructed()).hasSize(1);
        }
    }

    @Test
    void verifyVpTokenForDCQLRequest_withExplicitlyDisabledHolderBinding_passesFalseToKeyBindingValidation() {
        when(vpToken.hasKeyBinding()).thenReturn(false);
        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(false).build();

        try (MockedConstruction<SdJwtVcValidator> ignored = successfulValidatorConstruction()) {
            var result = dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(serializedVpToken, management, dcqlCredential);

            assertThat(result.sdJwt()).isEqualTo(vpToken);
            verify(sdJwtVpTokenVerifier).validateKeyBinding(eq(vpToken), eq(false), eq(management), any(SdJwtVcValidator.class));
        }
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
    void verifyVpTokenForDCQLRequest_whenHeaderValidationFails_throwsVerificationException() {
        var dcqlCredential = DcqlCredential.builder().requireCryptographicHolderBinding(false).build();

        try (MockedConstruction<SdJwtVcValidator> ignored = mockConstruction(SdJwtVcValidator.class,
                (mock, context) -> doThrow(new SdJwtVerificationException("bad header"))
                        .when(mock).validateAndSetHeader(vpToken))) {

            assertThatThrownBy(() -> dcqlVpTokenVerifier.verifyVpTokenForDCQLRequest(serializedVpToken, management, dcqlCredential))
                    .isInstanceOf(VerificationException.class);
        }
    }

    private MockedConstruction<SdJwtVcValidator> successfulValidatorConstruction() {
        return mockConstruction(SdJwtVcValidator.class, (mock, context) -> {
            doNothing().when(mock).validateAndSetHeader(vpToken);
            doNothing().when(mock).validateAndSetJwt(eq(vpToken), any());
        });
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
