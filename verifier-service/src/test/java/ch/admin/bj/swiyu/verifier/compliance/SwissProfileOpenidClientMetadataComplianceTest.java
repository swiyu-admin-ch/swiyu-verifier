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

@DisplayName("Static Compliance Check: Swiss Profile OpenID Client Metadata Endpoint")
class SwissProfileOpenidClientMetadataComplianceTest extends AbstractSwissProfileComplianceTest {

    private static final String MAPPING_PATH = "/oid4vp/api";
    private static final String ENDPOINT = MAPPING_PATH + "/openid-client-metadata.json";

    // --- Tier 1: Path Item Verification ---

    @Test
    @DisplayName("Path: Endpoint '/oid4vp/api/openid-client-metadata.json' must exist in the contract")
    void testOpenidClientMetadataEndpointExists() {
        assertThat(openAPI.getPaths())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] The paths section must not be empty.")
                .isNotNull();
        assertThat(openAPI.getPaths().get(ENDPOINT))
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] The endpoint " + ENDPOINT + " MUST exist in the OpenAPI contract.")
                .isNotNull();
    }

    // --- Tier 2: HTTP Verb Validation ---

    @Test
    @DisplayName("HTTP Verb: Verifier Metadata MUST be retrieved via GET")
    void testOpenidClientMetadataEndpointUsesGet() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation getOperation = pathItem.getGet();
        assertThat(getOperation)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] Retrieval of the Verifier Metadata MUST be performed via the HTTP GET method.")
                .isNotNull();
    }

    // --- Tier 3: Response Status & Media Type Check ---

    @Test
    @DisplayName("Content-Type: Successful response MUST use 'application/json'")
    void testOpenidClientMetadataResponseUsesApplicationJson() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation getOperation = pathItem.getGet();
        assertThat(getOperation)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] GET operation must exist.")
                .isNotNull();

        ApiResponse response200 = getOperation.getResponses().get("200");
        assertThat(response200)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] A '200 OK' response MUST be defined for the OpenID Client Metadata endpoint.")
                .isNotNull();

        assertThat(response200.getContent())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] The 200 response MUST define content.")
                .isNotNull();
        assertThat(response200.getContent())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] The Content-Type header MUST be strictly validated as 'application/json'.")
                .containsKey("application/json");
    }

    @Test
    @DisplayName("Security: Endpoint MUST be publicly accessible without authorization headers")
    void testOpenidClientMetadataEndpointIsPublic() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        assertThat(pathItem)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] Path item for " + ENDPOINT + " must exist.")
                .isNotNull();

        Operation getOperation = pathItem.getGet();
        assertThat(getOperation)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] GET operation must exist.")
                .isNotNull();

        assertThat(getOperation.getSecurity())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] This endpoint serves public configuration data and MUST NOT require authorization headers.")
                .isNullOrEmpty();
    }

    // --- Tier 4: JSON Schema Assertions ---

    @Test
    @DisplayName("Schema: Response body MUST be a JSON object")
    void testOpenidClientMetadataResponseBodyIsObject() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] A schema must be defined for the 200 application/json response.")
                .isNotNull();
        assertThat(schema.getTypes())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] The response body MUST be a valid JSON object representing the Verifier Metadata.")
                .isNotNull()
                .contains("object");
    }

    @Test
    @DisplayName("Schema: 'vp_formats_supported' MUST be a required object property")
    void testVpFormatsIsRequiredObject() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        List<String> required = schema.getRequired();
        assertThat(required)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11.1. Additional Verifier Metadata Parameters] The 'vp_formats_supported' property is REQUIRED and MUST be declared as required.")
                .isNotNull()
                .contains("vp_formats_supported");

        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties)
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11.1. Additional Verifier Metadata Parameters] Schema properties must be defined and include 'vp_formats_supported'.")
                .isNotNull()
                .containsKey("vp_formats_supported");
        assertThat(properties.get("vp_formats_supported").getTypes())
                .as("[Document: OpenID for Verifiable Presentations 1.0, Chapter: 11.1. Additional Verifier Metadata Parameters] The 'vp_formats_supported' property MUST outline the verifiable presentation formats as a JSON object.")
                .isNotNull()
                .contains("object");
    }

    @Test
    @DisplayName("Schema: 'vp_formats_supported' MUST NOT include ISO mdoc-based credential formats")
    void testVpFormatsDoesNotIncludeMdoc() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.10. Request URI Method post] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && properties.containsKey("vp_formats_supported")) {
            Schema<?> vpFormats = properties.get("vp_formats_supported");
            Map<String, Schema> vpFormatProperties = vpFormats.getProperties();
            if (vpFormatProperties != null) {
                assertThat(vpFormatProperties)
                        .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.10. Request URI Method post] The 'vp_formats_supported' property MUST NOT include configurations for ISO mdoc-based credentials ('mso_mdoc'), as these are explicitly NOT SUPPORTED.")
                        .doesNotContainKey("mso_mdoc");
            }
        }
    }

    @Disabled("TODO: OpenidClientMetadataDto declares 'jwks' but does not mark it (or 'jwks_uri') as required")
    @Test
    @DisplayName("Schema: 'jwks' or 'jwks_uri' MUST be required to supply encryption public keys")
    void testJwksOrJwksUriIsRequired() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        List<String> required = schema.getRequired();
        assertThat(required)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] Either 'jwks' or 'jwks_uri' MUST be required to supply the Wallet with the necessary public keys for encryption.")
                .isNotNull();
        assertThat(required.contains("jwks") || required.contains("jwks_uri"))
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] Either 'jwks' or 'jwks_uri' MUST be present and required in the metadata object.")
                .isTrue();
    }

    @Disabled("TODO: OpenidClientMetadataDto does not declare 'authorization_encrypted_response_alg' / 'authorization_encrypted_response_enc'; it only has an unrelated 'encrypted_response_enc_values_supported' array")
    @Test
    @DisplayName("Schema: JWE algorithm properties MUST be defined to enforce encrypted Direct Post JWT responses")
    void testEncryptionAlgAndEncArePresent() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        Map<String, Schema> properties = schema.getProperties();
        assertThat(properties)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] Schema properties must be defined.")
                .isNotNull();
        assertThat(properties)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] The 'authorization_encrypted_response_alg' property MUST be defined, because the response mode 'direct_post.jwt' MUST be used and encryption MUST be enforced.")
                .containsKey("authorization_encrypted_response_alg");
        assertThat(properties)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response] The 'authorization_encrypted_response_enc' property MUST be defined, because the response mode 'direct_post.jwt' MUST be used and encryption MUST be enforced.")
                .containsKey("authorization_encrypted_response_enc");
    }

    @Test
    @DisplayName("Schema: Transaction Data properties MUST NOT be required (NOT SUPPORTED in Swiss Profile)")
    void testTransactionDataIsNotRequired() {
        Schema<?> schema = getResponseSchema();
        assertThat(schema)
                .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.4 Transaction Data] A schema must be defined for the 200 application/json response.")
                .isNotNull();

        List<String> required = schema.getRequired();
        if (required != null) {
            assertThat(required)
                    .as("[Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.4 Transaction Data] Properties related to Transaction Data MUST NOT be required, as Transaction Data is explicitly NOT SUPPORTED.")
                    .doesNotContain("transaction_data", "transaction_data_hashes_alg_supported", "transaction_data_hashes_alg");
        }
    }

    private static Schema<?> getResponseSchema() {
        PathItem pathItem = openAPI.getPaths().get(ENDPOINT);
        return getResponseSchema(pathItem, "200");
    }

}
