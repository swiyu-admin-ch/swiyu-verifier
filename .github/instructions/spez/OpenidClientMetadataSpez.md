# OpenidClientMetadataSpez.md

**Static Compliance Check (AI Swagger Linting) - OID4VCI / Swiss Profile**

## Endpoint: `GET /oid4vp/api/openid-client-metadata.json`

This endpoint provides the static OpenID Verifier (Client) Metadata. It is utilized by the Wallet to dynamically obtain the Verifier's capabilities, including the supported verifiable presentation formats and essential cryptographic configurations. Since the Swiss Profile enforces an encrypted Direct Post JWT response, this metadata is critical for supplying the Wallet with the necessary public keys and accepted cryptographic algorithms.

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
* The `vp_formats_supported` property MUST NOT include configurations for ISO mdoc-based credentials, as these are explicitly NOT SUPPORTED [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.10. Request URI Method post].
* The property `jwks` (or alternatively `jwks_uri`) MUST be required and present in the metadata object to supply the Wallet with the necessary public keys for encryption [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response].
* Properties related to JWE algorithms (such as `authorization_encrypted_response_alg` and `authorization_encrypted_response_enc`) MUST be defined, because the response mode `direct_post.jwt` MUST be used and the usage of encryption MUST be enforced [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response].
* The schema MUST NOT require or expect properties related to Transaction Data, as Transaction Data is explicitly NOT SUPPORTED and SHOULD NOT be used in this swiss-profile-verification version [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.4 Transaction Data].