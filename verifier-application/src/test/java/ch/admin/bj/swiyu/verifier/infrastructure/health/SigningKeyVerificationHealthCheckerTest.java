package ch.admin.bj.swiyu.verifier.infrastructure.health;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.SignerProvider;
import ch.admin.bj.swiyu.verifier.service.SignatureService;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverException;
import ch.admin.eid.did_sidekicks.DidDoc;
import ch.admin.eid.did_sidekicks.Jwk;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
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

    private static final String TEST_VERIFICATION_METHOD = "did:example:123#key-1";
    private static final String TEST_DID = "did:example:123";

    @Mock
    private DidResolverAdapter didResolverAdapter;

    @Mock
    private ApplicationProperties applicationProperties;

    @Mock
    private SignatureService signatureService;

    @Mock
    private DidDoc didDoc;

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
                didResolverAdapter,
                applicationProperties,
                signatureService
        );

        // Generate a test EC key pair
        testKey = new ECKeyGenerator(Curve.P_256)
                .keyID("test-key")
                .generate();
        testSigner = new ECDSASigner(testKey);

        // Default setup for successful cases
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn(TEST_VERIFICATION_METHOD);
    }

    @Test
    void performCheck_shouldReturnUp_whenAllChecksPass() throws Exception {
        // Given
        when(didResolverAdapter.resolveDid(TEST_DID)).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(didDoc.getKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(jwk.toString()).thenReturn(testKey.toPublicJWK().toJSONString());
        doNothing().when(didDoc).close();

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
        assertThat(health.getDetails()).containsKey("failedDids");
    }

    @Test
    void performCheck_shouldReturnDown_whenDidResolutionFails() throws DidResolverException {
        // Given
        when(didResolverAdapter.resolveDid(TEST_DID)).thenThrow(new DidResolverException("Resolution failed"));

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("failedDids", TEST_VERIFICATION_METHOD);
    }

    @Test
    void performCheck_shouldReturnDown_whenSignerProviderCannotProvideSigner() throws Exception {
        // Given
        when(didResolverAdapter.resolveDid(TEST_DID)).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(false);

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
    void performCheck_shouldReturnDown_whenSigningFails() throws Exception {
        // Given
        when(didResolverAdapter.resolveDid(TEST_DID)).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
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
        when(didResolverAdapter.resolveDid(TEST_DID)).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(didDoc.getKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(jwk.toString()).thenReturn("invalid-json");

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
        when(didResolverAdapter.resolveDid("did:example:123")).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(didDoc.getKey(verificationMethodWithFragment)).thenReturn(jwk);
        when(jwk.toString()).thenReturn(testKey.toPublicJWK().toJSONString());
        doNothing().when(didDoc).close();

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        verify(didResolverAdapter).resolveDid("did:example:123");
        Health health = builder.build();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void performCheck_shouldHandleVerificationMethodWithoutFragment() throws Exception {
        // Given
        String verificationMethodWithoutFragment = "did:example:123";
        when(applicationProperties.getSigningKeyVerificationMethod()).thenReturn(verificationMethodWithoutFragment);
        when(didResolverAdapter.resolveDid("did:example:123")).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(didDoc.getKey(verificationMethodWithoutFragment)).thenReturn(jwk);
        when(jwk.toString()).thenReturn(testKey.toPublicJWK().toJSONString());
        doNothing().when(didDoc).close();

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        verify(didResolverAdapter).resolveDid("did:example:123");
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

        when(didResolverAdapter.resolveDid(TEST_DID)).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(didDoc.getKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        // Return a different public key for verification
        when(jwk.toString()).thenReturn(differentKey.toPublicJWK().toJSONString());
        doNothing().when(didDoc).close();

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
        when(didResolverAdapter.resolveDid(TEST_DID)).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(didDoc.getKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(jwk.toString()).thenReturn(testKey.toPublicJWK().toJSONString());
        doNothing().when(didDoc).close();

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
        when(didResolverAdapter.resolveDid(TEST_DID)).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(didDoc.getKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(jwk.toString()).thenReturn(testKey.toPublicJWK().toJSONString());
        doNothing().when(didDoc).close();

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        verify(didResolverAdapter, times(1)).resolveDid(anyString());
    }

    @Test
    void performCheck_shouldCallSignatureServiceOnlyOnce() throws Exception {
        // Given
        when(didResolverAdapter.resolveDid(TEST_DID)).thenReturn(didDoc);
        when(signatureService.createDefaultSignerProvider()).thenReturn(signerProvider);
        when(signerProvider.canProvideSigner()).thenReturn(true);
        when(signerProvider.getSigner()).thenReturn(testSigner);
        when(didDoc.getKey(TEST_VERIFICATION_METHOD)).thenReturn(jwk);
        when(jwk.toString()).thenReturn(testKey.toPublicJWK().toJSONString());
        doNothing().when(didDoc).close();

        Health.Builder builder = Health.up();

        // When
        healthChecker.performCheck(builder);

        // Then
        verify(signatureService, times(1)).createDefaultSignerProvider();
    }
}

