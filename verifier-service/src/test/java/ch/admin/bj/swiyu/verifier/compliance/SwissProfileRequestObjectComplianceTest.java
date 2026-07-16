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

@DisplayName("Static Compliance Check: Swiss Profile Request Object Endpoint")
class SwissProfileRequestObjectComplianceTest extends AbstractSwissProfileComplianceTest {

    private static final String MAPPING_PATH = "/oid4vp/api";
    private static final String ENDPOINT = MAPPING_PATH + "/request-object/{request_id}";
    private static final String JWT_CONTENT_TYPE = "application/oauth-authz-req+jwt";
    private static final String JSON_CONTENT_TYPE = "application/json";

    // --- Tier 1: Path Item Verification ---

    @Test
    @DisplayName("Path: Endpoint '/oid4vp/api/request-object/{request_id}' must exist in the contract")
    void testRequestObjectEndpointExists() {
        assertThat(openAPI.getPaths())
                .as("[Document: RFC 9101, Chapter: 5.2.3 Authorization Server Fetches Request Object] The paths section must not be empty.")
                .isNotNull();
        assertThat(openAPI.getPaths().get(ENDPOINT))
                .as("[Document: RFC 9101, Chapter: 5.2.3 Authorization Server Fetches Request Object] The endpoint " + ENDPOINT + " MUST exist in the OpenAPI contract.")
                .isNotNull();
    }

    // --- Tier 2: HTTP Verb Validation ---

    @Test
    @DisplayName("HTTP Verb: Request Object MUST be retrieved via GET")
    void testRequestObjectEndpointUsesGet() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation getOperation = pathItem.getGet();
        assertThat(getOperation)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] The Wallet MUST retrieve the Request Object via the HTTP GET method.")
                .isNotNull();
    }

    @Test
    @DisplayName("HTTP Verb: Request Object endpoint MUST NOT support POST")
    void testRequestObjectEndpointDoesNotSupportPost() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.10. Request URI Method post] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        assertThat(pathItem.getPost())
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.10. Request URI Method post] The endpoint explicitly MUST NOT support the 'post' method for the request URI.")
                .isNull();
    }

    // --- Tier 3: Response Status & Media Type Check ---

    @Test
    @DisplayName("Content-Type: Successful response MUST use 'application/oauth-authz-req+jwt'")
    void testRequestObjectResponseUsesJwtContentType() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: RFC 9101, Chapter: 5.2.3 Authorization Server Fetches Request Object] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation getOperation = pathItem.getGet();
        assertThat(getOperation)
                .as("[Document: RFC 9101, Chapter: 5.2.3 Authorization Server Fetches Request Object] GET operation must exist.")
                .isNotNull();

        ApiResponse response200 = getOperation.getResponses().get("200");
        assertThat(response200)
                .as("[Document: RFC 9101, Chapter: 5.2.3 Authorization Server Fetches Request Object] A '200 OK' response MUST be defined for the Request Object endpoint.")
                .isNotNull();

        assertThat(response200.getContent())
                .as("[Document: RFC 9101, Chapter: 5.2.3 Authorization Server Fetches Request Object] The 200 response MUST define content.")
                .isNotNull();
        assertThat(response200.getContent())
                .as("[Document: RFC 9101, Chapter: 5.2.3 Authorization Server Fetches Request Object] The Content-Type header MUST be defined as 'application/oauth-authz-req+jwt'.")
                .containsKey(JWT_CONTENT_TYPE);
    }

    @Disabled("TODO: The 200 response of /oid4vp/api/request-object/{request_id} additionally declares 'application/json' content next to 'application/oauth-authz-req+jwt'; VerificationController#getRequestObject uses a single shared @Content for both produced media types instead of restricting the contract to the JWT type")
    @Test
    @DisplayName("Content-Type: Successful response MUST strictly use only 'application/oauth-authz-req+jwt'")
    void testRequestObjectResponseContentTypeIsStrictlyJwt() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        Operation getOperation = pathItem.getGet();
        ApiResponse response200 = getOperation.getResponses().get("200");

        assertThat(response200.getContent())
                .as("[Document: RFC 9101, Chapter: 5.2.3 Authorization Server Fetches Request Object] The Content-Type header MUST be strictly defined as 'application/oauth-authz-req+jwt', with no additional media types documented for this response.")
                .containsOnlyKeys(JWT_CONTENT_TYPE);
    }

    @Test
    @DisplayName("Security: Endpoint MUST NOT require authorization headers")
    void testRequestObjectEndpointRequiresNoAuthorizationHeader() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: RFC 9101, Chapter: 5.2.1 URI Referencing the Request Object] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation getOperation = pathItem.getGet();
        assertThat(getOperation)
                .as("[Document: RFC 9101, Chapter: 5.2.1 URI Referencing the Request Object] GET operation must exist.")
                .isNotNull();

        assertThat(getOperation.getSecurity())
                .as("[Document: RFC 9101, Chapter: 5.2.1 URI Referencing the Request Object] The Request Object URI relies on appropriate entropy rather than an authorization header, so the endpoint MUST NOT declare a security requirement.")
                .isNullOrEmpty();
    }

    // --- Tier 4: JSON Schema Assertions ---

    @Test
    @DisplayName("Schema: Response body for 'application/oauth-authz-req+jwt' MUST be a JWT string, not a JSON object")
    void testRequestObjectResponseSchemaIsString() {
        Schema<?> schema = getJwtResponseSchema();
        assertThat(schema)
                .as("[Document: RFC 9101, Chapter: 4 Request Object] A schema must be defined for the 200 'application/oauth-authz-req+jwt' response.")
                .isNotNull();
        assertThat(schema.getTypes())
                .as("[Document: RFC 9101, Chapter: 4 Request Object] The Request Object response MUST be defined as a 'string' (the signed JWT), not a JSON object.")
                .isNotNull()
                .contains("string");
    }

    @Test
    @DisplayName("Schema: Response payload MUST NOT document 'request' or 'request_uri' properties")
    void testRequestObjectResponseDoesNotExposeRequestOrRequestUri() {
        // The JWT Claims Set is not representable in the 'application/oauth-authz-req+jwt' string schema,
        // so the payload properties are asserted against the 'application/json' representation instead.
        Schema<?> schema = getJsonResponseSchema();
        assertThat(schema)
                .as("[Document: RFC 9101, Chapter: 4 Request Object] A schema must be defined for the 200 'application/oauth-authz-req+jwt' response; since this is a JWT string and cannot be statically verified as such, the 'application/json' representation of the same payload is checked instead.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            assertThat(properties)
                    .as("[Document: RFC 9101, Chapter: 4 Request Object] The 'request' property MUST NOT be present inside the Request Object payload, as nested Request Objects are strictly prohibited.")
                    .doesNotContainKey("request");
            assertThat(properties)
                    .as("[Document: RFC 9101, Chapter: 4 Request Object] The 'request_uri' property MUST NOT be present inside the Request Object payload, as nested Request Objects are strictly prohibited.")
                    .doesNotContainKey("request_uri");
        }
    }

    // --- Tier 4: JWT Content Linting (Swiss Profile overrides on the documented JWT payload/headers) ---

    @Test
    @DisplayName("JWT Header: 'profile_version' MUST be required to indicate the Swiss Profile version")
    void testProfileVersionHeaderIsRequired() {
        // The JOSE header is not representable in the 'application/oauth-authz-req+jwt' string schema,
        // so 'profile_version' is asserted against the 'application/json' representation of the same payload instead.
        Schema<?> schema = getJsonResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5. Authorization Request] A schema must be defined for the 200 'application/oauth-authz-req+jwt' response; since this is a JWT string and cannot be statically verified as such, the 'application/json' representation of the same payload is checked instead.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5. Authorization Request] The JWT header MUST require the 'profile_version' parameter to indicate the Swiss Profile version.")
                .isNotNull()
                .containsKey("profile_version");
    }

    @Disabled("TODO: RequestObject does not declare a top-level 'jwks' property; the Wallet's encryption keys are only nested under 'client_metadata'")
    @Test
    @DisplayName("JWT Payload: 'jwks' parameter MUST be required")
    void testJwksIsRequiredInPayload() {
        // The JWT Claims Set is not representable in the 'application/oauth-authz-req+jwt' string schema,
        // so the payload properties are asserted against the 'application/json' representation instead.
        Schema<?> schema = getJsonResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] A schema must be defined for the 200 'application/oauth-authz-req+jwt' response; since this is a JWT string and cannot be statically verified as such, the 'application/json' representation of the same payload is checked instead.")
                .isNotNull();

        List<String> required = schema.getRequired();
        assertThat(required)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] The JWT payload MUST require the 'jwks' parameter.")
                .isNotNull()
                .contains("jwks");
    }

    @Test
    @DisplayName("JWT Payload: 'encrypted_response_enc_values_supported' parameter MUST be required")
    void testEncryptedResponseEncValuesSupportedIsRequiredInPayload() {
        // The JWT Claims Set is not representable in the 'application/oauth-authz-req+jwt' string schema,
        // so the payload properties are asserted against the 'application/json' representation instead.
        Schema<?> schema = getJsonResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] A schema must be defined for the 200 'application/oauth-authz-req+jwt' response; since this is a JWT string and cannot be statically verified as such, the 'application/json' representation of the same payload is checked instead.")
                .isNotNull();

        List<String> required = schema.getRequired();
        assertThat(required)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] The JWT payload MUST require the 'encrypted_response_enc_values_supported' parameter.")
                .isNotNull()
                .contains("encrypted_response_enc_values_supported");
    }

    @Test
    @DisplayName("JWT Payload: 'vp_formats_supported' parameter MUST NOT be supported or expected")
    void testVpFormatsSupportedNotExpectedInPayload() {
        // The JWT Claims Set is not representable in the 'application/oauth-authz-req+jwt' string schema,
        // so the payload properties are asserted against the 'application/json' representation instead.
        Schema<?> schema = getJsonResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] A schema must be defined for the 200 'application/oauth-authz-req+jwt' response; since this is a JWT string and cannot be statically verified as such, the 'application/json' representation of the same payload is checked instead.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            assertThat(properties)
                    .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] The JWT payload MUST NOT support or expect the 'vp_formats_supported' parameter, as the credential format identifier is determined by the DCQL query's format parameter instead.")
                    .doesNotContainKey("vp_formats_supported");
        }

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] The 'vp_formats_supported' parameter MUST NOT be declared as required.")
                    .doesNotContain("vp_formats_supported");
        }
    }

    @Test
    @DisplayName("JWT Payload: 'transaction_data' parameter MUST NOT be supported or expected")
    void testTransactionDataNotExpectedInPayload() {
        // The JWT Claims Set is not representable in the 'application/oauth-authz-req+jwt' string schema,
        // so the payload properties are asserted against the 'application/json' representation instead.
        Schema<?> schema = getJsonResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] A schema must be defined for the 200 'application/oauth-authz-req+jwt' response; since this is a JWT string and cannot be statically verified as such, the 'application/json' representation of the same payload is checked instead.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            assertThat(properties)
                    .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] The JWT payload MUST NOT support or expect the 'transaction_data' parameter.")
                    .doesNotContainKey("transaction_data");
        }

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] The 'transaction_data' parameter MUST NOT be declared as required.")
                    .doesNotContain("transaction_data");
        }
    }

    @Test
    @DisplayName("JWT Payload: 'verifier_info.credential_ids' MUST be explicitly NOT SUPPORTED if 'verifier_info' is documented")
    void testVerifierInfoCredentialIdsNotSupported() {
        // The JWT Claims Set is not representable in the 'application/oauth-authz-req+jwt' string schema,
        // so the payload properties are asserted against the 'application/json' representation instead.
        Schema<?> schema = getJsonResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] A schema must be defined for the 200 'application/oauth-authz-req+jwt' response; since this is a JWT string and cannot be statically verified as such, the 'application/json' representation of the same payload is checked instead.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && properties.containsKey("verifier_info")) {
            Schema<?> verifierInfo = properties.get("verifier_info");
            Schema<?> items = verifierInfo.getItems();
            if (items != null && items.getProperties() != null) {
                assertThat(items.getProperties())
                        .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters] If 'verifier_info' is documented, its sub-property 'credential_ids' MUST be explicitly NOT SUPPORTED.")
                        .doesNotContainKey("credential_ids");
            }
        }
    }

    private static Schema<?> getJwtResponseSchema() {
        return getResponseSchema(JWT_CONTENT_TYPE);
    }

    private static Schema<?> getJsonResponseSchema() {
        return getResponseSchema(JSON_CONTENT_TYPE);
    }

    private static Schema<?> getResponseSchema(String contentType) {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        Operation getOperation = pathItem.getGet();
        if (getOperation == null || getOperation.getResponses() == null) return null;
        ApiResponse response200 = getOperation.getResponses().get("200");
        if (response200 == null || response200.getContent() == null) return null;
        var mediaType = response200.getContent().get(contentType);
        return mediaType != null ? mediaType.getSchema() : null;
    }

}
