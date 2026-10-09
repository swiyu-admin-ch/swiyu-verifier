package ch.admin.bj.swiyu.verifier.service.sdjwt;

import ch.admin.bj.swiyu.sdjwtverifier.SdJwt;
import ch.admin.bj.swiyu.sdjwtverifier.SdJwtVcValidator;
import ch.admin.bj.swiyu.sdjwtverifier.exception.SdJwtVerificationException;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.ConfigurationOverride;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock.DEFAULT_ISSUER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;

class HolderKeyBindingVerificationServiceTest {

    private HolderKeyBindingVerificationService holderKeyBindingVerificationService;
    private Management management;

    private static final String TEST_NONCE = "test-nonce";
    private static final String prefix = "prefix";
    private static final String clientId = "did:example:verifier";


    @BeforeEach
    void setUp() {
        ApplicationProperties applicationProperties = mock(ApplicationProperties.class);
        when(applicationProperties.getClientId()).thenReturn(clientId);
        when(applicationProperties.getClientIdPrefix()).thenReturn(prefix);
        VerificationProperties verificationProperties = mock(VerificationProperties.class);
        when(verificationProperties.getAcceptableProofTimeWindowSeconds()).thenReturn(120);

        management = mock(Management.class);
        when(management.getId()).thenReturn(UUID.randomUUID());
        when(management.getAcceptedIssuerDids()).thenReturn(List.of(DEFAULT_ISSUER_ID));
        when(management.getRequestNonce()).thenReturn(TEST_NONCE);
        when(management.getConfigurationOverride()).thenReturn(new ConfigurationOverride(null, null, null, null, null, null));

        holderKeyBindingVerificationService = new HolderKeyBindingVerificationService(applicationProperties, verificationProperties);
    }

    @Test
    void validateKeyBinding_whenHolderBindingNotRequiredAndMissing_thenSkipsValidator() throws SdJwtVerificationException {
        SdJwt sdJwt = mock(SdJwt.class);
        when(sdJwt.hasKeyBinding()).thenReturn(false);
        SdJwtVcValidator validator = mock(SdJwtVcValidator.class);

        holderKeyBindingVerificationService.validateKeyBinding(sdJwt, false, management, validator);

        verify(validator, never()).validateKeyBinding(sdJwt, prefix + ":" + clientId, TEST_NONCE, 120);
    }

    @Test
    void validateKeyBinding_whenHolderBindingRequiredAndMissing_thenThrowsHolderBindingMismatch() {
        SdJwt sdJwt = mock(SdJwt.class);
        SdJwtVcValidator validator = mock(SdJwtVcValidator.class);

        when(sdJwt.hasKeyBinding()).thenReturn(false);

        VerificationException ex = assertThrows(VerificationException.class,
                () -> holderKeyBindingVerificationService.validateKeyBinding(sdJwt, true, management, validator));

        assertEquals(HOLDER_BINDING_MISMATCH, ex.getErrorResponseCode());
    }

    @Test
    void validateKeyBinding_whenHolderBindingPresent_thenDelegatesWithExpectedAudienceAndNonce() throws SdJwtVerificationException {
        SdJwt sdJwt = mock(SdJwt.class);
        SdJwtVcValidator validator = mock(SdJwtVcValidator.class);

        when(sdJwt.hasKeyBinding()).thenReturn(true);

        holderKeyBindingVerificationService.validateKeyBinding(sdJwt, true, management, validator);

        verify(validator).validateKeyBinding(sdJwt, prefix + ":" + clientId, TEST_NONCE, 120);
    }

    @Test
    void validateKeyBinding_whenValidatorRejectsProof_thenThrowsHolderBindingMismatch() throws SdJwtVerificationException {
        SdJwt sdJwt = mock(SdJwt.class);
        SdJwtVcValidator validator = mock(SdJwtVcValidator.class);

        when(sdJwt.hasKeyBinding()).thenReturn(true);
        doThrow(new SdJwtVerificationException("invalid proof"))
                .when(validator)
                .validateKeyBinding(sdJwt, prefix + ":" + clientId, TEST_NONCE, 120);

        VerificationException ex = assertThrows(VerificationException.class,
                () -> holderKeyBindingVerificationService.validateKeyBinding(sdJwt, true, management, validator));

        assertEquals(HOLDER_BINDING_MISMATCH, ex.getErrorResponseCode());
    }

}