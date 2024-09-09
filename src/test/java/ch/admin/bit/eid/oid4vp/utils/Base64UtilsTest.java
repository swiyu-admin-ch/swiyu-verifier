package ch.admin.bit.eid.oid4vp.utils;

import ch.admin.bit.eid.oid4vp.fixtures.KeyFixtures;
import org.junit.jupiter.api.Test;

import static ch.admin.bit.eid.oid4vp.utils.Base64Utils.encodeBase64;
import static org.assertj.core.api.Assertions.assertThat;

class Base64UtilsTest {

    @Test
    void decodeMultibaseKeyTest() {
        // GIVEN
        String multikey = KeyFixtures.issuerPublicKeyAsMultibaseKey();

        // WHEN
        byte[] decodedKey = Base64Utils.decodeMultibaseKey(multikey);

        // THEN
        assertThat(encodeBase64(decodedKey))
                .isEqualTo("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQgjeqGSdu-2jq8-n78-6fXk2Yh22lQKBYCnu5FWPvKtat3wFEsQXqNHYgPXBxWmOBw5l2PE_gUDUJqGJSc1LuQ");
    }
}