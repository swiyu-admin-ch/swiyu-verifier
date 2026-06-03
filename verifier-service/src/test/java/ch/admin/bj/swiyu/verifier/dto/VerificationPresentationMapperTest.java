package ch.admin.bj.swiyu.verifier.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VerificationPresentationMapperTest {

    /**
     * Ensures that an already-parsed DCQL map is mapped without additional parsing steps.
     */
    @Test
    void toDcqlPresentation_whenVpTokenIsMap_thenConvertsSuccessfully() {
        var union = new VerificationPresentationUnionDto();
        union.setVp_token(Map.of("my_credential", List.of("jwt-1", "jwt-2")));

        var dcql = VerificationPresentationMapper.toDcqlPresentation(union);

        assertNotNull(dcql);
        assertEquals(Map.of("my_credential", List.of("jwt-1", "jwt-2")), dcql.getVpToken());
    }

    /**
     * Ensures that a JSON string representing the DCQL map can be parsed.
     */
    @Test
    void toDcqlPresentation_whenVpTokenIsJsonString_thenParsesSuccessfully() {
        var union = new VerificationPresentationUnionDto();
        union.setVp_token("{\"my_credential\":[\"jwt-1\",\"jwt-2\"]}");

        var dcql = VerificationPresentationMapper.toDcqlPresentation(union);

        assertNotNull(dcql);
        assertEquals(Map.of("my_credential", List.of("jwt-1", "jwt-2")), dcql.getVpToken());
    }

    /**
     * Real-world fixture: sometimes the vp_token field is provided as a JSON string that contains the
     * full request object (wrapping the actual DCQL map under a top-level "vp_token" key).
     */
    @Test
    void toDcqlPresentation_whenVpTokenIsRealWorldJsonString_thenParsesSuccessfully() {
        var union = new VerificationPresentationUnionDto();

        var realWorldJson = """
                {"vp_token":{"defaultTestDcqlCredentialId":["eyJraWQiOiJURVNUX0lTU1VFUl9JRCNrZXktMSIsInR5cCI6InZjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJURVNUX0lTU1VFUl9JRCIsIl9zZCI6WyJHbldFZ0JtVnRySEk3SnBFMWdZXzZGOUVDc2IxMnBpVUZDT2xseDQzOUFJIiwicklUNmxmNWY2ckc3UnpIa2NNT3JzdFJsbHh2SVJSMUwxSlR2ZFFVVUJKRSIsInZWelQtQXRCeHBISFZ4dUxfS0FYWUozR0hSS2p1Yk1pOGoyVVZ2Y2p6Zk0iXSwiY25mIjp7Imp3ayI6eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6Ik5tQUM5Ym8xcVpOUVkyblpDSDlVTTJjXzJxMXV0R2JWNnY0RU1XSmRkNEUiLCJ5IjoibUxJbmRBa3JxNlE5Yi1kWTN0TVBUeVFZSEtDdGZYM0xBNGtxZmVwM2NqYyJ9fSwiaWF0IjoxNzcyNDQ3MjkwLCJ2Y3QiOiJkZWZhdWx0VGVzdFZDVCJ9.ZnyBCPjZkUMH13z4k48VAD755ZcuOcKaPqZ5GhkaKMuiKvZOD1fUXw9ESiEDoPkKzdHcHBqeT8D-mqXqt3I9zA~WyJMLVJwNnpvMDVtZkxJT1MyUE5DODdBIiwiYmlydGhkYXRlIiwiMTk0OS0wMS0yMiJd~WyI2dWFIYi00VzM4SWs0Vl9MUVlFZHlBIiwibGFzdF9uYW1lIiwiVGVzdExhc3ROYW1lIl0~WyJaU3ZvbG9yTjVPLVIwVjd6bkl5Qk9nIiwiZmlyc3RfbmFtZSIsIlRlc3RGaXJzdG5hbWUiXQ~eyJ0eXAiOiJrYitqd3QiLCJhbGciOiJFUzI1NiJ9.eyJzZF9oYXNoIjoiNHJPQzZPX3o4cS1qZDRnUHh5UUgyRUJYWjc0SkktNjZGYkROQmUtZ0F4TSIsImF1ZCI6ImRpZDpleGFtcGxlOjEyMzQ1IiwiaWF0IjoxNzcyNDQ3MjkwLCJub25jZSI6IlAydlo4REtBdFR1Q0lVMU03ZGFXTEE2NUd6b2E3NnRMIn0.-KDLpTlnktNs0w_w9IBy3EhNqICXVlsxUkrVt82ED1usxKVG8l71dn0l8AIIqhrrz9ynbDyim6seI0s3jpJ2YA"]}}""";

        union.setVp_token(realWorldJson);

        var dcql = VerificationPresentationMapper.toDcqlPresentation(union);

        assertNotNull(dcql);
        assertNotNull(dcql.getVpToken());
        assertTrue(dcql.getVpToken().containsKey("defaultTestDcqlCredentialId"));
        assertEquals(1, dcql.getVpToken().get("defaultTestDcqlCredentialId").size());

        var parsedToken = dcql.getVpToken().get("defaultTestDcqlCredentialId").getFirst();
        assertNotNull(parsedToken);
        assertTrue(parsedToken.startsWith("eyJ"));
        assertTrue(parsedToken.contains("."));
    }


    @Test
    void toDcqlPresentation_whenVpTokenIsNull_thenThrows() {
        var union = new VerificationPresentationUnionDto();
        union.setVp_token(null);

        assertThrows(IllegalArgumentException.class, () -> VerificationPresentationMapper.toDcqlPresentation(union));
    }

    @Test
    void toDcqlPresentation_whenVpTokenJsonInvalid_thenThrowsAndMentionsParse() {
        var union = new VerificationPresentationUnionDto();
        union.setVp_token("{not-valid-json");

        var ex = assertThrows(IllegalArgumentException.class, () -> VerificationPresentationMapper.toDcqlPresentation(union));
        assertTrue(ex.getMessage().contains("Failed to parse vp_token as DCQL format"));
    }
}

