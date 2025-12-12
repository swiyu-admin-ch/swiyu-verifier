package ch.admin.bj.swiyu.verifier.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VpTokenDCQLVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.adapters.SdjwtPresentationVerifierAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SdjwtPresentationVerifierAdapterTest {

    @Mock
    private VpTokenDCQLVerifier vpTokenVerifier;

    @InjectMocks
    private SdjwtPresentationVerifierAdapter adapter;

    @Test
    void verify_wrapsTokenInSdJwtAndDelegatesToDomainVerifier() {
        String vpToken = "vp-token";
        Management management = new Management();
        DcqlCredential dcqlCredential = new DcqlCredential();

        // Call the adapter
        adapter.verify(vpToken, management, dcqlCredential);

        // Capture the SdJwt passed to the delegate
        ArgumentCaptor<SdJwt> sdJwtCaptor = ArgumentCaptor.forClass(SdJwt.class);
        verify(vpTokenVerifier).verifyVpTokenForDCQLRequest(sdJwtCaptor.capture(), eq(management), eq(dcqlCredential));
        SdJwt passedToDelegate = sdJwtCaptor.getValue();

        // We cannot rely on SdJwt.equals, so we compare its String representation
        assertThat(passedToDelegate.getJwt()).contains(vpToken);
    }
}
