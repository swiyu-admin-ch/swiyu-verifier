package ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.experimental.UtilityClass;

import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/**
 * Fixtures for public / private keys we can use them in tests.
 */
@UtilityClass
public class KeyFixtures {
    private static final ECKey DEFAULT_ISSUER_KEY; 
    static {
        try {
            DEFAULT_ISSUER_KEY = new ECKeyGenerator(Curve.P_256)
            .keyID("key-1")
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();
        } catch (JOSEException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Returns a private key of an issuer as ECKey.
     */
    public static ECKey issuerKey() {
        return DEFAULT_ISSUER_KEY;
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
        return issuerKey().toPublicJWK().toJSONString();
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
