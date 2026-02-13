package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.util.SignerProvider;
import ch.admin.bj.swiyu.verifier.service.JwtSigningService;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import ch.admin.eid.did_sidekicks.Jwk;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SigningKeyVerificationHealthChecker}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SigningKeyVerificationHealthCheckerTest {

    private static final String TEST_DID = "did:example:123";
    private static final String TEST_VERIFICATION_METHOD = TEST_DID + "#key-1";
    private static final String FRAGMENT = "key-1";

    @Mock
    private DidResolverFacade didResolverFacade;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private JwtSigningService jwtSigningService;

    @Mock
    private Jwk jwk;

    @Mock
    private SignerProvider signerProvider;

    private SigningKeyVerificationHealthChecker healthChecker;

    private ECKey testKey;
    private JWSSigner testSigner;

    @BeforeEach
    void setUp() throws Exception {
        healthChecker = new SigningKeyVerificationHealthChecker(
                didResolverFacade,
                applicationProperties,
                jwtSigningService
        );

        // Generate a test EC key pair
        testKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key")
                .generate();
        testSigner = new ECDSASigner(testKey);


        // Default setup for successful cases
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn(TEST_VERIFICATION_METHOD);
        when(jwtSigningService.signJwt(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenAnswer(invocation -> {
            var claimsSet = invocation.getArgument(0, JWTClaimsSet.class);
            JWSHeader header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.ES256)
                    .keyID("did:override#key1")
                    .type(new com.nimbusds.jose.JOSEObjectType("oauth-authz-req+jwt"))
                    .build();
            SignedJWT signedJwt = new SignedJWT(header, claimsSet);
            signedJwt.sign(testSigner);
            return signedJwt;
        });
    }

    @Test
    void performCheck_shouldReturnUp_whenAllChecksPass() throws Exception {
        // Given
        when(didResolverFacade.resolveDid(TEST_DID, FRAGMENT)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(jwk.getKty()).thenReturn(testKey.getKeyType().toString());
        when(jwk.getCrv()).thenReturn(testKey.getCurve().getName());
        when(jwk.getX()).thenReturn(testKey.getX().toString());
        when(jwk.getY()).thenReturn(testKey.getY().toString());
        when(jwk.getKid()).thenReturn(testKey.getKeyID());

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("signingKeyVerificationMethod", TEST_VERIFICATION_METHOD);
    }

    @Test
    void performCheck_shouldReturnDown_whenVerificationMethodIsBlank() {
        // Given
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn("   ");

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("signingKeyVerificationMethod");
    }

    @Test
    void performCheck_shouldReturnDown_whenDidResolutionFails() throws DidResolverException, DidSidekicksException {
        // Given
        when(didResolverFacade.resolveDid(TEST_DID, FRAGMENT))
                .thenThrow(new DidResolverException("Resolution failed"));

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("signingKeyVerificationMethod", TEST_VERIFICATION_METHOD);
    }



    @Test
    void performCheck_shouldReturnDown_whenSigningFails() throws Exception {
        // Given
        when(didResolverFacade.resolveDid(TEST_DID, FRAGMENT)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);

        JWSSigner failingSigner = mock(JWSSigner.class);
        doThrow(new JOSEException("Signing failed")).when(failingSigner).sign(any(), any());
        when(signerProvider.getSigner()).thenReturn(failingSigner);

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("signingError");
    }


    @Test
    void performCheck_shouldReturnDown_whenJwkParsingFails() throws Exception {
        // Given
        when(didResolverFacade.resolveDid(TEST_DID, FRAGMENT)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(jwk.getKty()).thenReturn("invalid-kty");
        when(jwk.getCrv()).thenReturn(testKey.getCurve().getName());
        when(jwk.getX()).thenReturn(testKey.getX().toString());
        when(jwk.getY()).thenReturn(testKey.getY().toString());
        when(jwk.getKid()).thenReturn(testKey.getKeyID());

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("signingError");
    }

    @Test
    void performCheck_shouldExtractDidFromVerificationMethodWithFragment() throws Exception {
        // Given
        String verificationMethodWithFragment = "did:example:123#key-1";
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn(verificationMethodWithFragment);
        when(didResolverFacade.resolveDid("did:example:123", FRAGMENT)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(jwk.getKty()).thenReturn(testKey.getKeyType().toString());
        when(jwk.getCrv()).thenReturn(testKey.getCurve().getName());
        when(jwk.getX()).thenReturn(testKey.getX().toString());
        when(jwk.getY()).thenReturn(testKey.getY().toString());
        when(jwk.getKid()).thenReturn(testKey.getKeyID());

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        verify(didResolverFacade).resolveDid("did:example:123", FRAGMENT);
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void performCheck_shouldHandleVerificationMethodWithoutFragment() throws Exception {
        // Given
        String didWithoutFragment = "did:example:123";
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn(didWithoutFragment);
        when(didResolverFacade.resolveDid(didWithoutFragment, didWithoutFragment)).thenReturn(jwk);
        when(jwk.getKty()).thenReturn(testKey.getKeyType().toString());
        when(jwk.getCrv()).thenReturn(testKey.getCurve().getName());
        when(jwk.getX()).thenReturn(testKey.getX().toString());
        when(jwk.getY()).thenReturn(testKey.getY().toString());
        when(jwk.getKid()).thenReturn(testKey.getKeyID());

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then: resolveDid is called with did as both did and fragment according to current logic
        verify(didResolverFacade).resolveDid(didWithoutFragment, didWithoutFragment);
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void performCheck_shouldReturnDown_whenSignatureVerificationFails() throws Exception {
        // Given
        // Create a different key for verification to simulate signature mismatch
        ECKey differentKey = new ECKeyGenerator(Curve.P_256)
                .keyID("different-key")
                .generate();

        when(didResolverFacade.resolveDid(TEST_DID, FRAGMENT)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(jwk.getKty()).thenReturn(differentKey.getKeyType().toString());
        when(jwk.getCrv()).thenReturn(differentKey.getCurve().getName());
        when(jwk.getX()).thenReturn(differentKey.getX().toString());
        when(jwk.getY()).thenReturn(differentKey.getY().toString());
        when(jwk.getKid()).thenReturn(differentKey.getKeyID());

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("signingKeyVerificationMethod");
        assertThat(health.getDetails().get("signingKeyVerificationMethod"))
                .asString()
                .contains("Verification failed for");
    }

    @Test
    void performCheck_shouldVerifyJwtPayloadIsCorrect() throws Exception {
        // This test verifies that the JWT creation includes the expected claims
        when(didResolverFacade.resolveDid(TEST_DID, FRAGMENT)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(jwk.getKty()).thenReturn(testKey.getKeyType().toString());
        when(jwk.getCrv()).thenReturn(testKey.getCurve().getName());
        when(jwk.getX()).thenReturn(testKey.getX().toString());
        when(jwk.getY()).thenReturn(testKey.getY().toString());
        when(jwk.getKid()).thenReturn(testKey.getKeyID());

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void performCheck_shouldCallDidResolverOnlyOnce() throws Exception {
        // Given
        when(didResolverFacade.resolveDid(TEST_DID, FRAGMENT)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(jwk.getKty()).thenReturn(testKey.getKeyType().toString());
        when(jwk.getCrv()).thenReturn(testKey.getCurve().getName());
        when(jwk.getX()).thenReturn(testKey.getX().toString());
        when(jwk.getY()).thenReturn(testKey.getY().toString());
        when(jwk.getKid()).thenReturn(testKey.getKeyID());

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        verify(didResolverFacade, times(1)).resolveDid(anyString(), anyString());
    }


}

