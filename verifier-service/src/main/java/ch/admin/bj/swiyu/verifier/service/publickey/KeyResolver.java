package ch.admin.bj.swiyu.verifier.service.publickey;

import com.nimbusds.jose.jwk.JWK;

@FunctionalInterface
public interface KeyResolver {
    JWK resolveKey(String keyId);
}

