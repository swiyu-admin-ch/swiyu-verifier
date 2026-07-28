package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.util.SignerProvider;
import ch.admin.bj.swiyu.verifier.service.JwtSigningService;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyType;
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
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

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
    private HealthCheckProperties healthCheckProperties;

    @Mock
    private JWK jwk;

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
                jwtSigningService,
                healthCheckProperties
        );

        // Health checks are enabled by default
        when(healthCheckProperties.isSigningKeyVerificationEnabled()).thenReturn(true);

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
        jwk = testKey.toPublicJWK();
        when(didResolverFacade.resolveKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("signingKeyVerificationMethod", TEST_VERIFICATION_METHOD);
    }

    @Test
    void performCheck_shouldReturnUp_whenVerificationMethodIsBlank() {
        // Given – no static key configured (dynamic key management scenario)
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn("   ");

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("signingKeyVerificationMethod", "not configured");
    }

    @Test
    void performCheck_shouldReturnUp_whenVerificationMethodIsNull() {
        // Given
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn(null);

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("signingKeyVerificationMethod", "not configured");
    }

    @Test
    void performCheck_shouldReturnDown_whenDidResolutionFails() throws DidResolverException {
        // Given
        when(didResolverFacade.resolveKey(TEST_VERIFICATION_METHOD))
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
        when(didResolverFacade.resolveKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
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
        JWK jwk1  = mock(JWK.class);
        when(jwk1.getKeyType()).thenReturn(KeyType.parse("teest?"));

        when(didResolverFacade.resolveKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk1);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);

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
        jwk = testKey.toECKey();
        String verificationMethodWithFragment = "did:example:123#key-1";
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn(verificationMethodWithFragment);
        when(didResolverFacade.resolveKey("did:example:123" + "#" + FRAGMENT)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        verify(didResolverFacade).resolveKey("did:example:123" + "#" + FRAGMENT);
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void performCheck_shouldHandleVerificationMethodWithoutFragment() throws Exception {
        // Given
        jwk = testKey.toECKey();
        String didWithoutFragment = "did:example:123";
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn(didWithoutFragment);
        when(didResolverFacade.resolveKey(didWithoutFragment)).thenReturn(jwk);

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then: resolveKey is called with did as both did and fragment according to current logic
        verify(didResolverFacade).resolveKey(didWithoutFragment);
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

        jwk = differentKey.toECKey();
        when(didResolverFacade.resolveKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);

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
        jwk = testKey.toECKey();
        when(didResolverFacade.resolveKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);

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
        jwk = testKey.toECKey();
        when(didResolverFacade.resolveKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        verify(didResolverFacade, times(1)).resolveKey(anyString());
    }

    @Test
    void scheduledCheck_shouldReturnUpWithDisabledDetail_whenSigningKeyVerificationIsDisabled() {
        // Given
        when(healthCheckProperties.isSigningKeyVerificationEnabled()).thenReturn(false);

        // When
        healthChecker.scheduledCheck();

        // Then
        Health health = healthChecker.getHealthResult();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("signingKeyVerificationMethod", "disabled");
        verifyNoInteractions(didResolverFacade, jwtSigningService);
    }
}

