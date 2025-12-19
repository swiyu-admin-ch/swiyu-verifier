package ch.admin.bj.swiyu.verifier.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.dto.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VerifiableCredentialExtractor;
import ch.admin.bj.swiyu.verifier.service.oid4vp.adapters.SdJwtPresentationVerificationStrategy;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.LegacyPresentationVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SdJwtPresentationVerificationStrategyTest {

    @Mock
    private LegacyPresentationVerifier legacyPresentationVerifier;

    @InjectMocks
    private SdJwtPresentationVerificationStrategy strategy;

    @Test
    void verify_extractsCredentialAndDelegatesToPresentationVerifier() {
        String vpToken = "vp-token";
        Management management = new Management();
        PresentationSubmissionDto submission = mock(PresentationSubmissionDto.class);

        String extractedCredential = "extracted-credential";
        String expectedResult = "{\"result\":true}";

        try (MockedStatic<VerifiableCredentialExtractor> extractorMock = mockStatic(VerifiableCredentialExtractor.class)) {
            extractorMock.when(() -> VerifiableCredentialExtractor.extractVerifiableCredential(vpToken, management, submission))
                    .thenReturn(extractedCredential);
            given(legacyPresentationVerifier.verify(extractedCredential, management)).willReturn(expectedResult);

            String result = strategy.verify(vpToken, management, submission);

            assertThat(result).isEqualTo(expectedResult);
            extractorMock.verify(() -> VerifiableCredentialExtractor.extractVerifiableCredential(vpToken, management, submission));
            verify(legacyPresentationVerifier).verify(extractedCredential, management);
        }
    }

    @Test
    void getSupportedFormat_returnsSdJwtFormatConstant() {
        assertThat(strategy.getSupportedFormat()).isEqualTo(SdjwtCredentialVerifier.CREDENTIAL_FORMAT);
    }
}

