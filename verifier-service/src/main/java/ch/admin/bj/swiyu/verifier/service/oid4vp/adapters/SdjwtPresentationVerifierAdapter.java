package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.service.oid4vp.DcqlVpTokenVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.LegacyPresentationVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Adapter implementation of {@link LegacyPresentationVerifier} that delegates VP token verification
 * to the existing {@link DcqlVpTokenVerifier} domain service.
 */
@Service
@Primary
@RequiredArgsConstructor
public class SdjwtPresentationVerifierAdapter implements PresentationVerifier {

    private final DcqlVpTokenVerifier delegate;

    /**
     * Wraps the given VP token into an {@link SdJwt} value object and delegates the
     * verification to {@link DcqlVpTokenVerifier#verifyVpTokenForDCQLRequest(SdJwt, Management, DcqlCredential)}.
     *
     * @param vpToken   the raw VP token to be verified
     * @param management the management configuration used to control verification rules
     * @return the verified {@link SdJwt} instance
     */
    @Override
    public SdJwt verify(String vpToken, Management management, DcqlCredential dcqlCredential) {
        SdJwt sdJwt = new SdJwt(vpToken);
        return delegate.verifyVpTokenForDCQLRequest(sdJwt, management, dcqlCredential);
    }
}
