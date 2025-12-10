package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VpTokenVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Adapter implementation of {@link PresentationVerifier} that delegates VP token verification
 * to the existing {@link VpTokenVerifier} domain service.
 */
@Service
@Primary
@RequiredArgsConstructor
public class VpTokenVerifierAdapter implements PresentationVerifier<SdJwt> {

    private final VpTokenVerifier delegate;

    /**
     * Wraps the given VP token into an {@link SdJwt} value object and delegates the
     * verification to {@link VpTokenVerifier#verifyVpToken(SdJwt, Management)}.
     *
     * @param vpToken   the raw VP token to be verified
     * @param management the management configuration used to control verification rules
     * @return the verified {@link SdJwt} instance
     */
    @Override
    public SdJwt verify(String vpToken, Management management) {
        SdJwt sdJwt = new SdJwt(vpToken);
        return delegate.verifyVpToken(sdJwt, management);
    }
}
