package ch.admin.bj.swiyu.verifier.oid4vp.common.base64;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;

@UtilityClass
public class Base64Utils {

    public static String decodeBase64(String base64EncodedString) {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(base64EncodedString);
        return new String(decodedBytes);
    }

    public static String decodeBase64(byte[] base64Encoded) {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(base64Encoded);
        return new String(decodedBytes);
    }

    @NotNull
    public static String encodeBase64(byte[] hashDigest) {
        return new String(Base64.getUrlEncoder().withoutPadding().encode(hashDigest));
    }

    /**
     * Decodes a <a href="https://github.com/multiformats/multibase?tab=readme-ov-file#multibase-table">multibase key</a>.
     */
    public static byte[] decodeMultibaseKey(String multikey) {
        var base64UrlEncodedKey = multikey;
        if (!base64UrlEncodedKey.startsWith("u")) {
            throw new UnsupportedOperationException("Failed to decode multikey. Only Base64 Url encoded keys " +
                    "which start with 'u' are supported at the moment.");
        }
        base64UrlEncodedKey = base64UrlEncodedKey.substring(1);
        return Base64.getUrlDecoder().decode(base64UrlEncodedKey);
    }
}
