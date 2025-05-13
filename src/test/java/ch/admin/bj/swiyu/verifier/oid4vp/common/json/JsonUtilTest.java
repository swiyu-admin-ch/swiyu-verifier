package ch.admin.bj.swiyu.verifier.oid4vp.common.json;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.ParseException;

class JsonUtilTest {

    @Test
    void testGetJsonObject() throws ParseException {
        var claims = JWTClaimsSet.parse("""
                {
                "iss": "https://example.com",
                "list": ["1", "2", "3"],
                "status_list": {
                    "bits": 2,
                    "version": "1.0",
                    "lst": "eNrtwQEBAAAAgiD_r25IQAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHwYYagAAQ"
                    }
                }
                """).getClaims();
        var statusListClaim = claims.get("status_list");
        Assertions.assertDoesNotThrow(() -> JsonUtil.getJsonObject(statusListClaim));
        var versionClaim = claims.get("version");
        Assertions.assertThrows(IllegalArgumentException.class, () -> JsonUtil.getJsonObject(versionClaim));
        var listClaim = claims.get("list");
        Assertions.assertThrows(IllegalArgumentException.class, () -> JsonUtil.getJsonObject(listClaim));

    }
}
