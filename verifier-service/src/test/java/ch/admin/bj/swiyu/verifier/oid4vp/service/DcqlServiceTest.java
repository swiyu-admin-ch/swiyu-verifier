package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.service.DcqlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DcqlServiceTest {
    private ObjectMapper objectMapper;
    private Map<String, Object> exampleData;
    private SdJwt sdJwt;
    @BeforeEach
    void setUp() throws JsonProcessingException {
        objectMapper = new ObjectMapper();
        // OID4VP 1.0 7.3 Claims Path Pointer Example https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-7.3
        exampleData = objectMapper.readValue("""
                {
                  "name": "Arthur Dent",
                  "address": {
                    "street_address": "42 Market Street",
                    "locality": "Milliways",
                    "postal_code": "12345"
                  },
                  "degrees": [
                    {
                      "type": "Bachelor of Science",
                      "university": "University of Betelgeuse"
                    },
                    {
                      "type": "Master of Science",
                      "university": "University of Betelgeuse"
                    }
                  ],
                  "nationalities": ["British", "Betelgeusian"]
                }
                """, Map.class);
        sdJwt = mock(SdJwt.class);
        var claims = mock(JWTClaimsSet.class);
        when(sdJwt.getClaims()).thenReturn(claims);
        when(claims.getClaims()).thenReturn(exampleData);
    }


    @Test
    void selection_whenNotContained_thenIllegalArgumentException() {
        var requestClaim = createSimpleDCQLClaim("doesNotExist");
        var claims = List.of(requestClaim);
        assertThrows(IllegalArgumentException.class, () -> DcqlService.containsRequestedFields(sdJwt, claims));
    }

    /**
     * ["name"]: The claim name with the value Arthur Dent is selected.
     */
    @Test
    void simpleSelection_thenSuccess() {
        var requestClaim = createSimpleDCQLClaim("name");
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    /**
     * ["address"]: The claim address with its sub-claims as the value is selected.
     */
    @Test
    void objectSelection_thenSuccess() {
        var requestClaim = createSimpleDCQLClaim("address");
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    /**
     * ["address", "street_address"]: The claim street_address with the value 42 Market Street is selected
     */
    @Test
    void nestedSelection_thenSuccess() {
        var requestClaim = createSimpleDCQLClaim("address", "street_address");
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    /**
     * ["address", "street_address"]: The claim street_address with the value 42 Market Street is selected
     */
    @Test
    void nestedSelection_whenMissing_thenIllegalArgumentException() {
        var requestClaim = createSimpleDCQLClaim("address", "street_address_does_not_exist");
        var claims = List.of(requestClaim);
        assertThrows(IllegalArgumentException.class, () -> DcqlService.containsRequestedFields(sdJwt, claims));
    }

    /**
     * ["address", "street_address"]: The claim street_address with the value 42 Market Street is selected
     */
    @Test
    void nestedSelection_whenNotNested_thenIllegalArgumentException() {
        var requestClaim = createSimpleDCQLClaim("address", "street_address", "does_not_exist");
        var claims = List.of(requestClaim);
        assertThrows(IllegalArgumentException.class, () -> DcqlService.containsRequestedFields(sdJwt, claims));
    }

    /**
     * ["degrees", null, "type"]: All type claims in the degrees array are selected.
     */
    @Test
    void arraySelection_whenAll_thenSuccess() {
        var requestClaim = createSimpleDCQLClaim("degrees", null, "type");
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    /**
     * ["degrees", null, "type"]: All type claims in the degrees array are selected.
     */
    @Test
    void arraySelection_whenAll_andNotExists_thenIllegalArgumentException() {
        var requestClaim = createSimpleDCQLClaim("degrees", null, "type_b");
        var claims = List.of(requestClaim);
        assertThrows(IllegalArgumentException.class, () -> DcqlService.containsRequestedFields(sdJwt, claims));
    }

    /**
     * ["nationalities", 1]: The second nationality is selected.
     */
    @Test
    void arraySelection_whenIndex_thenSuccess() {
        var requestClaim = createSimpleDCQLClaim("nationalities", 1);
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    /**
     * ["degrees", null, "type"]: All type claims in the degrees array are selected.
     */
    @Test
    void arraySelection_whenIndexDoesNotExist_thenIllegalArgumentException() {
        var requestClaim = createSimpleDCQLClaim("nationalities", 2);
        var claims = List.of(requestClaim);
        assertThrows(IllegalArgumentException.class, () -> DcqlService.containsRequestedFields(sdJwt, claims));
    }

    @Test
    void arraySelection_whenIndexAndFurther_thenSuccess() {
        var requestClaim = createSimpleDCQLClaim("degrees", 1, "type");
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    /**
     * ["name"]: The claim name with the value Arthur Dent is selected and check if the value is Arthur Dent
     */
    @Test
    void simpleSelection_whenValue_thenSuccess() {
        var requestClaim = new DcqlClaim(null, List.of("name"), List.of("Arthur Dent"));
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    @Test
    void simpleSelection_whenValueSurplus_thenSuccess() {
        var requestClaim = new DcqlClaim(null, List.of("name"), List.of("Arthur Dent", "Bruce Wayne"));
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    @Test
    void simpleSelection_whenValueMismatch_thenIllegalArgumentException() {
        var requestClaim = new DcqlClaim(null, List.of("name"), List.of("Bruce Wayne"));
        var claims = List.of(requestClaim);
        assertThrows(IllegalArgumentException.class, () -> DcqlService.containsRequestedFields(sdJwt, claims));
    }

    @Test
    void arraySelection_whenMultipleValues_thenSuccess() {
        var paths = new LinkedList<>();
        paths.add("degrees");
        paths.add(null);
        paths.add("type");
        var requestClaim = new DcqlClaim(null, paths, List.of("Bachelor of Science", "Master of Science"));
        assertDoesNotThrow(() -> DcqlService.containsRequestedFields(sdJwt, List.of(requestClaim)));
    }

    @Test
    void arraySelection_whenValueMissing_thenThrowIllegalArgumentException() {
        var paths = new LinkedList<>();
        paths.add("degrees");
        paths.add(null);
        paths.add("type");
        var requestClaim = new DcqlClaim(null, paths, List.of("Bachelor of Science", "Master of Arts"));
        var claims = List.of(requestClaim);
        assertThrows(IllegalArgumentException.class, () -> DcqlService.containsRequestedFields(sdJwt, claims));
    }

    private DcqlClaim createSimpleDCQLClaim(Object... claimPath) {
        return new DcqlClaim(null, Arrays.stream(claimPath).toList(), null);
    }
}
