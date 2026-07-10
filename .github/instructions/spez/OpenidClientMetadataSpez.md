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
* The `vp_formats_supported` property MUST NOT include configurations for ISO mdoc-based credentials, as these are explicitly NOT SUPPORTED [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
* The property `jwks` MUST be required and present (by value) in the metadata object to supply the Wallet with the necessary public keys for encryption. `jwks_uri` MUST NOT be used for the encryption key, because the Swiss Profile passes the encryption key by value within the signed Request Object [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response].
* The response encryption metadata parameter `encrypted_response_enc_values_supported` MUST be defined, because the response mode `direct_post.jwt` MUST be used and the usage of encryption MUST be enforced. The JWE key management algorithm (`alg`) is determined by the `alg` of the JWK provided in `jwks`; therefore the legacy JARM-style parameters `authorization_encrypted_response_alg` and `authorization_encrypted_response_enc` MUST NOT be used [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.3. Response Mode "direct_post.jwt"].
* The schema MUST NOT require or expect properties related to Transaction Data, as Transaction Data is explicitly NOT SUPPORTED and SHOULD NOT be used in this swiss-profile-verification version [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.4 Transaction Data].