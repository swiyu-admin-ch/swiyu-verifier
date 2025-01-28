package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

import com.nimbusds.jose.JWSSigner;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class SignerProvider {
    private final JWSSigner signer;

    public boolean canProvideSigner() {
        return signer != null;
    }

}
