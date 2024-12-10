package ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.experimental.UtilityClass;

import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * Fixtures for public / private keys we can use them in tests.
 */
@UtilityClass
public class KeyFixtures {
    private static final String DEFAULT_ISSUER_PRIVATE_KEY = "-----BEGIN EC PRIVATE KEY-----\nMHcCAQEEIDqMm9PvL4vpyFboAwaeViQsH30CkaDcVtRniZPezFxpoAoGCCqGSM49\nAwEHoUQDQgAEQgjeqGSdu+2jq8+n78+6fXk2Yh22lQKBYCnu5FWPvKtat3wFEsQX\nqNHYgPXBxWmOBw5l2PE/gUDUJqGJSc1LuQ==\n-----END EC PRIVATE KEY-----";

    /**
     * Returns a private key of an issuer as ECKey.
     */
    public static ECKey issuerKey() {
        return toEcKey(DEFAULT_ISSUER_PRIVATE_KEY);
    }

    /**
     * Returns the public key of the issuer above as byte array.
     */
    public static byte[] issuerPublicKeyEncoded() throws JOSEException {
        return issuerKey().toPublicKey().getEncoded();
    }

    /**
     * Returns the public key of the issuer above as base64 url encoded multikey.
     */
    public static String issuerPublicKeyAsMultibaseKey() {
        try {
            return toBase64UrlEncodedMultibaseKey(issuerKey().toECPublicKey());
        } catch (JOSEException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns the JSON Web Key (JWK) of the issuers public key (as json, not encoded).
     */
    public static String issuerPublicKeyAsJsonWebKey() {
        var json = issuerKey().toPublicJWK().toJSONString();
        return json;
    }

    private static ECKey toEcKey(String pemEncodedObjects) {
        try {
            return JWK.parseFromPEMEncodedObjects(pemEncodedObjects).toECKey();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    private static String toBase64UrlEncodedMultibaseKey(ECPublicKey publicKey) {
        byte[] encodedKey = publicKey.getEncoded();
        String base64UrlEncodedKey = Base64.getUrlEncoder().encodeToString(encodedKey);
        return "u" + base64UrlEncodedKey;
    }

    public static ECKey holderKey() {
        try {
            return new ECKeyGenerator(Curve.P_256).generate();
        } catch (JOSEException e) {
            throw new AssertionError(e);
        }
    }
}
