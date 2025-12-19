package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.domain.SdjwtCredentialVerifier;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.oid4vp.adapters.SdjwtCredentialVerifierAdapterLegacy;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SdjwtCredentialVerifierAdapterTest {

    @Mock
    private IssuerPublicKeyLoader issuerPublicKeyLoader;

    @Mock
    private StatusListReferenceFactory statusListReferenceFactory;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private VerificationProperties verificationProperties;

    @Mock
    private ApplicationProperties applicationProperties;

    @InjectMocks
    private SdjwtCredentialVerifierAdapterLegacy adapter;

    @Test
    void verify_delegatesToLegacyVerifier() {
        String vpToken = "vp-token";
        Management management = new Management();
        String expectedResult = "{\"claims\":\"value\"}";

        try (MockedConstruction<SdjwtCredentialVerifier> mocked = mockConstruction(
                SdjwtCredentialVerifier.class,
                (mock, context) -> {
                    // Ensure the constructor parameters are passed through as expected
                    assertThat(context.arguments()).hasSize(7);
                    assertThat(context.arguments().get(0)).isEqualTo(vpToken);
                    assertThat(context.arguments().get(1)).isEqualTo(management);
                    assertThat(context.arguments().get(2)).isEqualTo(issuerPublicKeyLoader);
                    assertThat(context.arguments().get(3)).isEqualTo(statusListReferenceFactory);
                    assertThat(context.arguments().get(4)).isEqualTo(objectMapper);
                    assertThat(context.arguments().get(5)).isEqualTo(verificationProperties);
                    assertThat(context.arguments().get(6)).isEqualTo(applicationProperties);

                    // Stub the verification result
                    org.mockito.BDDMockito.given(mock.verifyPresentation()).willReturn(expectedResult);
                })) {

            String result = adapter.verify(vpToken, management);

            assertThat(result).isEqualTo(expectedResult);
            assertThat(mocked.constructed()).hasSize(1);
            verify(mocked.constructed().getFirst()).verifyPresentation();
        }
    }
}
