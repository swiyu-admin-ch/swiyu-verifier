package ch.admin.bj.swiyu.verifier.compliance;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Static Compliance Check: Swiss Profile VCT Endpoint")
class SwissProfileXXXComplianceTest extends AbstractSwissProfileComplianceTest {

    private static final String MAPPING_PATH = "/oid4vci";
    private static final String ENDPOINT = MAPPING_PATH + "/vct/{metadataKey}";

    // --- Tier 1: Path Item Verification ---

    @Test
    @DisplayName("Path: Endpoint '/oid4vci/vct/{metadataKey}' must exist in the contract")
    void testVctEndpointExists() {
        assertThat(openAPI.getPaths())
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] The paths section must not be empty.")
                .isNotNull();
        assertThat(openAPI.getPaths().get(ENDPOINT))
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] The endpoint " + ENDPOINT + " MUST exist in the OpenAPI contract.")
                .isNotNull();
    }

    // --- Tier 2: HTTP Verb Validation ---

    @Test
    @DisplayName("HTTP Verb: Credential Type Metadata MUST be retrieved via GET")
    void testVctEndpointUsesGet() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation getOperation = pathItem.getGet();
        assertThat(getOperation)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] Retrieval of Type Metadata MUST be performed via the HTTP GET method.")
                .isNotNull();
    }

    // --- Tier 3: Response Status & Media Type Check ---

    @Test
    @DisplayName("Content-Type: Successful response MUST use 'application/json'")
    void testVctResponseUsesApplicationJson() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation getOperation = pathItem.getGet();
        assertThat(getOperation)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] GET operation must exist.")
                .isNotNull();

        ApiResponse response200 = getOperation.getResponses().get("200");
        assertThat(response200)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] A '200 OK' response MUST be defined for the VCT endpoint.")
                .isNotNull();

        assertThat(response200.getContent())
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] The 200 response MUST define content.")
                .isNotNull();
        assertThat(response200.getContent())
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] A successful response MUST use 'application/json' as the content type.")
                .containsKey("application/json");
    }

    // --- Tier 4: JSON Schema Assertions ---

    @Disabled("TODO EIDOMNI-1151 Fix and enhance VCT interface compliance tests")
    @Test
    @DisplayName("Schema: Response body MUST be a JSON object (not a primitive)")
    void testVctResponseBodyIsObject() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] A schema must be defined for the 200 application/json response.")
                .isNotNull();
        assertThat(schema.getTypes())
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The Type Metadata document MUST be formatted as a JSON object.")
                .isNotNull()
                .contains("object");
    }

    @Disabled("TODO EIDOMNI-1151 Fix and enhance VCT interface compliance tests")
    @Test
    @DisplayName("Schema: 'vct' MUST be a required string property")
    void testVctPropertyIsRequiredAndString() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        List<String> required = schema.getRequired();
        assertThat(required)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'vct' property MUST be declared as required.")
                .isNotNull()
                .contains("vct");

        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] Schema properties must be defined and include 'vct'.")
                .isNotNull()
                .containsKey("vct");
        assertThat(properties.get("vct").getTypes())
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'vct' property MUST be defined as a string.")
                .isNotNull()
                .contains("string");
    }

    @Disabled("TODO EIDOMNI-1127: Fixing Compliance OID4VCI / Swiss profile")
    @Test
    @DisplayName("Schema: 'extends' MUST NOT be required (Type Metadata inheritance NOT SUPPORTED in Swiss Profile)")
    void testExtendsIsNotRequired() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile for Verifiable Credentials, Chapter: 5.4. Extending Type Metadata] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: Swiss Profile for Verifiable Credentials, Chapter: 5.4. Extending Type Metadata] The 'extends' property MUST NOT be required — extending Type Metadata is NOT SUPPORTED in the Swiss Profile.")
                    .doesNotContain("extends");
        }
    }

    @Disabled("TODO EIDOMNI-1127: Fixing Compliance OID4VCI / Swiss profile")
    @Test
    @DisplayName("Schema: 'extends#integrity' MUST NOT be required (NOT SUPPORTED in Swiss Profile)")
    void testExtendsIntegrityIsNotRequired() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile for Verifiable Credentials, Chapter: 5.4. Extending Type Metadata] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: Swiss Profile for Verifiable Credentials, Chapter: 5.4. Extending Type Metadata] The 'extends#integrity' property MUST NOT be required — extending Type Metadata is NOT SUPPORTED in the Swiss Profile.")
                    .doesNotContain("extends#integrity");
        }
    }

    @Disabled("TODO EIDOMNI-1127: Fixing Compliance OID4VCI / Swiss profile")
    @Test
    @DisplayName("Schema: 'name' MUST be an optional string property")
    void testNameIsOptionalString() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && properties.containsKey("name")) {
            assertThat(properties.get("name").getTypes())
                    .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] If 'name' is defined, it MUST be of type 'string'.")
                    .isNotNull()
                    .contains("string");
        }

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'name' property is OPTIONAL and MUST NOT be required.")
                    .doesNotContain("name");
        }
    }

    @Disabled("TODO EIDOMNI-1127: Fixing Compliance OID4VCI / Swiss profile")
    @Test
    @DisplayName("Schema: 'description' MUST be an optional string property")
    void testDescriptionIsOptionalString() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && properties.containsKey("description")) {
            assertThat(properties.get("description").getTypes())
                    .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] If 'description' is defined, it MUST be of type 'string'.")
                    .isNotNull()
                    .contains("string");
        }

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'description' property is OPTIONAL and MUST NOT be required.")
                    .doesNotContain("description");
        }
    }

    @Disabled("TODO EIDOMNI-1127: Fixing Compliance OID4VCI / Swiss profile")
    @Test
    @DisplayName("Schema: 'display' MUST be an optional array of objects")
    void testDisplayIsOptionalArray() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && properties.containsKey("display")) {
            assertThat(properties.get("display").getTypes())
                    .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'display' property MUST be of type 'array'.")
                    .isNotNull()
                    .contains("array");
        }

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'display' property is OPTIONAL and MUST NOT be required.")
                    .doesNotContain("display");
        }
    }

    @Disabled("TODO EIDOMNI-1127: Fixing Compliance OID4VCI / Swiss profile")
    @Test
    @DisplayName("Schema: 'claims' MUST be an optional array of objects")
    void testClaimsIsOptionalArray() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && properties.containsKey("claims")) {
            assertThat(properties.get("claims").getTypes())
                    .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'claims' property MUST be of type 'array'.")
                    .isNotNull()
                    .contains("array");
        }

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'claims' property is OPTIONAL and MUST NOT be required.")
                    .doesNotContain("claims");
        }
    }

    private static Schema<?> getResponseSchema() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        return getResponseSchema(pathItem, "200");
    }


}
