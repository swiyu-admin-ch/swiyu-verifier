package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VpTokenVerifier;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.PresentationVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Adapter implementing PresentationVerifier by delegating to the existing VpTokenVerifier.
 */
@Service
@Primary
@RequiredArgsConstructor
public class VpTokenVerifierAdapter implements PresentationVerifier<SdJwt> {

    private final VpTokenVerifier delegate;

    @Override
    public SdJwt verify(String vpToken, Management management) {
        SdJwt sdJwt = new SdJwt(vpToken);
        return delegate.verifyVpToken(sdJwt, management);
    }
}
