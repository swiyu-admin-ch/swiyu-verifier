/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.base64;

import ch.admin.bj.swiyu.verifier.common.util.base64.Base64Utils;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.KeyFixtures;
import org.junit.jupiter.api.Test;

import static ch.admin.bj.swiyu.verifier.common.util.base64.Base64Utils.encodeBase64;
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
