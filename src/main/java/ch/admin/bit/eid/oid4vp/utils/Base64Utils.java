package ch.admin.bit.eid.oid4vp.utils;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;

@UtilityClass
public class Base64Utils {

    public static String decodeBase64(String base64EncodedString) {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(base64EncodedString);
        return new String(decodedBytes);
    }

    @NotNull
    public static String encodeBase64(byte[] hashDigest) {
        return new String(Base64.getUrlEncoder().withoutPadding().encode(hashDigest));
    }
}
