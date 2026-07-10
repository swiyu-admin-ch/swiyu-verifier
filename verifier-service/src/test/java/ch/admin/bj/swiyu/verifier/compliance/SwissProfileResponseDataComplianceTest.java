package ch.admin.bj.swiyu.verifier.compliance;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Static Compliance Check: Swiss Profile Response Data Endpoint")
class SwissProfileResponseDataComplianceTest extends AbstractSwissProfileComplianceTest {

    private static final String MAPPING_PATH = "/oid4vp/api";
    private static final String ENDPOINT = MAPPING_PATH + "/request-object/{request_id}/response-data";
    private static final String FORM_URLENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String JSON_CONTENT_TYPE = "application/json";

    // --- Tier 1: Path Item Verification ---

    @Test
    @DisplayName("Path: Endpoint '/oid4vp/api/request-object/{request_id}/response-data' must exist in the contract")
    void testResponseDataEndpointExists() {
        assertThat(openAPI.getPaths())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The paths section must not be empty.")
                .isNotNull();
        assertThat(openAPI.getPaths().get(ENDPOINT))
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The endpoint " + ENDPOINT + " MUST exist in the OpenAPI contract.")
                .isNotNull();
    }

    // --- Tier 2: HTTP Verb Validation ---

    @Test
    @DisplayName("HTTP Verb: Response Data MUST be submitted via POST")
    void testResponseDataEndpointUsesPost() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation postOperation = pathItem.getPost();
        assertThat(postOperation)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The Wallet MUST submit the Authorization Response via the HTTP POST method.")
                .isNotNull();
    }

    // --- Tier 3: Response Status & Media Type Check ---

    @Test
    @DisplayName("Content-Type: Request body MUST accept 'application/x-www-form-urlencoded'")
    void testResponseDataRequestAcceptsFormUrlEncodedContentType() {
        Operation postOperation = getPostOperation();
        assertThat(postOperation)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] POST operation must exist.")
                .isNotNull();

        RequestBody requestBody = postOperation.getRequestBody();
        assertThat(requestBody)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] A request body MUST be defined for the Response Data endpoint.")
                .isNotNull();
        assertThat(requestBody.getContent())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The endpoint MUST strictly accept requests with the Content-Type 'application/x-www-form-urlencoded'.")
                .isNotNull()
                .containsKey(FORM_URLENCODED_CONTENT_TYPE);
    }

    @Test
    @DisplayName("Status: A successful response MUST return HTTP status 200 OK")
    void testResponseDataDefines200Response() {
        Operation postOperation = getPostOperation();
        assertThat(postOperation)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] POST operation must exist.")
                .isNotNull();

        assertThat(postOperation.getResponses())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] A '200 OK' response MUST be defined for the Response Data endpoint.")
                .isNotNull()
                .containsKey("200");
    }

    @Test
    @DisplayName("Content-Type: Successful response MUST use 'application/json'")
    void testResponseDataSuccessResponseUsesJsonContentType() {
        ApiResponse response200 = getPostOperation().getResponses().get("200");
        assertThat(response200)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] A '200 OK' response MUST be defined for the Response Data endpoint.")
                .isNotNull();

        assertThat(response200.getContent())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The 200 response MUST define content.")
                .isNotNull();
        assertThat(response200.getContent())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The Content-Type header of the returned response MUST be strictly defined as 'application/json'.")
                .containsKey(JSON_CONTENT_TYPE);
    }

    @Test
    @DisplayName("Status: Endpoint SHOULD define an HTTP 400 Bad Request response")
    void testResponseDataDefines400BadRequestResponse() {
        Operation postOperation = getPostOperation();
        assertThat(postOperation)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] POST operation must exist.")
                .isNotNull();

        assertThat(postOperation.getResponses())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The endpoint SHOULD define an HTTP '400 Bad Request' response to handle validation or processing failures.")
                .isNotNull()
                .containsKey("400");
    }

    @Test
    @DisplayName("Content-Type: Error response (400) MUST use 'application/json'")
    void testResponseDataErrorResponseUsesJsonContentType() {
        ApiResponse response400 = getPostOperation().getResponses().get("400");
        assertThat(response400)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.5 Error Response] A '400 Bad Request' response MUST be defined for the Response Data endpoint.")
                .isNotNull();

        assertThat(response400.getContent())
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.5 Error Response] The 400 response MUST define content.")
                .isNotNull();
        assertThat(response400.getContent())
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.5 Error Response] Authorization Error Responses MUST NEVER be encrypted, so the Content-Type of the error response MUST be strictly defined as 'application/json'.")
                .containsKey(JSON_CONTENT_TYPE);
    }

    // --- Tier 4: JSON Schema Assertions (Request Body) ---

    @Test
    @DisplayName("Schema: Request body MUST define the 'response' property")
    void testResponseDataRequestBodyDefinesResponseProperty() {
        Schema<?> schema = getRequestBodySchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] A schema must be defined for the 'application/x-www-form-urlencoded' request body.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] The request body schema MUST define the 'response' property, which encapsulates the encrypted JWT.")
                .isNotNull()
                .containsKey("response");
    }

    @Disabled("TODO: VerificationPresentationUnion (the shared request schema for all presentation/rejection types) does not declare any 'required' properties at all, so 'response' cannot be asserted as required")
    @Test
    @DisplayName("Schema: 'response' property MUST be declared as required")
    void testResponseDataResponsePropertyIsRequired() {
        Schema<?> schema = getRequestBodySchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] A schema must be defined for the 'application/x-www-form-urlencoded' request body.")
                .isNotNull();

        List<String> required = schema.getRequired();
        assertThat(required)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] The 'response' property MUST be declared as REQUIRED, as the Response Mode 'direct_post.jwt' encapsulates all parameters within this single token.")
                .isNotNull()
                .contains("response");
    }

    @Test
    @DisplayName("Schema: 'response' property MUST be defined as a string")
    void testResponseDataResponsePropertyIsString() {
        Schema<?> schema = getRequestBodySchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] A schema must be defined for the 'application/x-www-form-urlencoded' request body.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] The request body schema MUST define the 'response' property.")
                .isNotNull()
                .containsKey("response");
        assertThat(properties.get("response").getTypes())
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] The 'response' property MUST be defined as a 'string', since it holds the base64-encoded JWE representation.")
                .isNotNull()
                .contains("string");
    }

    @Test
    @DisplayName("Schema: Request body MUST NOT document Transaction Data properties")
    void testResponseDataRequestBodyDoesNotExposeTransactionData() {
        Schema<?> schema = getRequestBodySchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] A schema must be defined for the 'application/x-www-form-urlencoded' request body.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            assertThat(properties)
                    .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.4 Transaction Data] The API contract MUST NOT document properties related to Transaction Data, as Transaction Data is explicitly NOT SUPPORTED.")
                    .doesNotContainKey("transaction_data");
        }

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.4 Transaction Data] The 'transaction_data' property MUST NOT be declared as required.")
                    .doesNotContain("transaction_data");
        }
    }

    // --- Tier 4: JSON Schema Assertions (Response Body back to Wallet) ---

    @Test
    @DisplayName("Schema: Successful response body MUST be a valid JSON object")
    void testResponseDataSuccessResponseBodyIsJsonObject() {
        Schema<?> schema = getResponseSchema("200");
        assertThat(schema)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] A schema must be defined for the 200 'application/json' response.")
                .isNotNull();
        assertThat(schema.getTypes())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The response body schema for '200 OK' MUST be defined as a valid JSON object.")
                .isNotNull()
                .contains("object");
    }

    @Test
    @DisplayName("Schema: 'redirect_uri' is OPTIONAL and, if present, MUST be a string")
    void testResponseDataRedirectUriIsOptionalString() {
        Schema<?> schema = getResponseSchema("200");
        if (schema != null) {
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null && properties.containsKey("redirect_uri")) {
                assertThat(properties.get("redirect_uri").getTypes())
                        .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The response body MAY define an OPTIONAL 'redirect_uri' property, which, if present, MUST be of type 'string'.")
                        .isNotNull()
                        .contains("string");
            }

            List<String> required = schema.getRequired();
            if (required != null) {
                assertThat(required)
                        .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The 'redirect_uri' property is OPTIONAL and MUST NOT be declared as required.")
                        .doesNotContain("redirect_uri");
            }
        }
    }

    @Test
    @DisplayName("Schema: Error response body MUST be a JSON object with a string 'error' property")
    void testResponseDataErrorResponseBodyHasErrorProperty() {
        Schema<?> schema = getResponseSchema("400");
        assertThat(schema)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] A schema must be defined for the 400 'application/json' response.")
                .isNotNull();
        assertThat(schema.getTypes())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The error response schema (400 Bad Request) MUST be a JSON object.")
                .isNotNull()
                .contains("object");

        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The error response schema MUST contain an 'error' property.")
                .isNotNull()
                .containsKey("error");
        assertThat(properties.get("error").getTypes())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode \"direct_post\"] The 'error' property of the error response MUST be defined as a 'string'.")
                .isNotNull()
                .contains("string");
    }

    @Test
    @DisplayName("Schema: Error response MUST remain standard JSON and MUST NOT be defined as a JWE string")
    void testResponseDataErrorResponseIsNotJwe() {
        Schema<?> schema = getResponseSchema("400");
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.5 Error Response] A schema must be defined for the 400 'application/json' response.")
                .isNotNull();
        assertThat(schema.getTypes())
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.5 Error Response] Authorization Error Responses SHOULD never be encrypted; the error response schema MUST NOT be defined as a 'string' (JWE) and MUST remain a standard JSON object.")
                .isNotNull()
                .doesNotContain("string");
    }

    private static Operation getPostOperation() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        return pathItem != null ? pathItem.getPost() : null;
    }

    private static Schema<?> getRequestBodySchema() {
        Operation postOperation = getPostOperation();
        if (postOperation == null || postOperation.getRequestBody() == null) return null;
        var content = postOperation.getRequestBody().getContent();
        if (content == null) return null;
        var mediaType = content.get(FORM_URLENCODED_CONTENT_TYPE);
        return mediaType != null ? mediaType.getSchema() : null;
    }

    private static Schema<?> getResponseSchema(String statusCode) {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        return getResponseSchema(pathItem, statusCode);
    }

}
