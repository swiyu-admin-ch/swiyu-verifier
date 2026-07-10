# OpenidClientMetadataSpez.md

**Static Compliance Check (AI Swagger Linting) - OID4VP / Swiss Profile**

## Endpoint: `GET /oid4vp/api/openid-client-metadata.json`

This endpoint provides the static OpenID Verifier (Client) Metadata. It is utilized by the Wallet to obtain the Verifier's capabilities, including the supported verifiable presentation formats and essential cryptographic configurations. Since the Swiss Profile enforces an encrypted Direct Post JWT response, this metadata is critical for supplying the Wallet with the necessary public keys and accepted cryptographic algorithms.

**HTTP Behavior & Content-Type**
* The endpoint MUST respond with an HTTP Status `200 OK` when the metadata is successfully retrieved [Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata].
* The `Content-Type` header MUST be strictly validated as `application/json` [Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata].

**Security & Headers**
* This endpoint serves public configuration data and MUST be publicly accessible without requiring authorization headers [Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata].

**JSON Schema / Request Body Assertions**
* *Not applicable. As a standard HTTP GET request, no request body is expected or processed.*

**JSON Schema / Response Body Assertions**
* The response body MUST be a valid JSON object representing the Verifier Metadata [Document: OpenID for Verifiable Presentations 1.0, Chapter: 11. Verifier Metadata].
* The property `vp_formats_supported` is REQUIRED and MUST be defined as a JSON object outlining the verifiable presentation formats the Verifier supports [Document: OpenID for Verifiable Presentations 1.0, Chapter: 11.1. Additional Verifier Metadata Parameters].
* The `vp_formats_supported` property MUST NOT include configurations for ISO mdoc-based credentials, as these are explicitly NOT SUPPORTED [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 7.2. Semantics for ISO mdoc-based credentials].
* The property `jwks` MUST be REQUIRED and present (by value) in the metadata object to supply the Wallet with the necessary public keys for encryption. `jwks_uri` MUST NOT be used for the encryption key, because the Swiss Profile passes the encryption key by value [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
* The response encryption metadata parameter `encrypted_response_enc_values_supported` MUST be REQUIRED and present, because the response mode `direct_post.jwt` MUST be used and the usage of encryption MUST be enforced [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
* The legacy JARM-style parameters `authorization_encrypted_response_alg` and `authorization_encrypted_response_enc` MUST NOT be used, because the JWE key management algorithm (`alg`) is inherently determined by the `alg` of the JWK provided in `jwks` [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.3. Response Mode "direct_post.jwt"].
* The schema MUST NOT require or expect properties related to Transaction Data, as Transaction Data is explicitly NOT SUPPORTED and SHOULD NOT be used in this swiss-profile-verification version [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.4 Transaction Data].

*Note for the Test Generator:* DO NOT write OpenAPI Schema assertions for the properties `logo_uri` or localized versions of `client_name` (e.g., `client_name#en`). According to the Swiss Profile [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 11 Verifier Metadata], these are OPTIONAL and specific format rules apply (such as Base64 Data-URLs for the logo). However, because they are transported dynamically via `additionalProperties` maps in the implementation, they are invisible to static schema linting. Assertions for these properties MUST be handled in separate runtime integration tests instead.

