package ch.admin.bj.swiyu.verifier.oid4vp.common.json;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.text.ParseException;

public class JsonUtilTest {

    @Test
    public void testGetJsonObject() throws ParseException {
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
        Assertions.assertDoesNotThrow(() -> JsonUtil.getJsonObject(claims.get("status_list")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> JsonUtil.getJsonObject(claims.get("version")));
        Assertions.assertThrows(IllegalArgumentException.class, () -> JsonUtil.getJsonObject(claims.get("list")));

    }
}
