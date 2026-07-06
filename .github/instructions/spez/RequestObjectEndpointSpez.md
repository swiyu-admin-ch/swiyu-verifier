### RequestObjectEndpointSpez.md

**Static Compliance Check (AI Swagger Linting) - OID4VP / Swiss Profile**

#### Endpoint: `GET /oid4vp/api/request-object/{request_id}`

This endpoint is used to pass the Authorization Request Object by reference. The Wallet fetches the Request Object from this URI via an HTTP GET request. The returned Request Object contains the authorization request parameters packaged as a signed JSON Web Token (JWT).

**HTTP Behavior & Content-Type**
* The endpoint MUST handle HTTP GET requests. The `request_uri_method` parameter is explicitly NOT SUPPORTED and the Wallet will only make HTTP GET requests [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
* The endpoint explicitly MUST NOT support the `post` method for the request URI [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.10. Request URI Method post].
* The endpoint MUST respond with an HTTP Status `200 OK` upon successful retrieval of the Request Object [Document: RFC 9101: The OAuth 2.0 Authorization Framework: JWT-Secured Authorization Request (JAR), Chapter: 5.2.3 Authorization Server Fetches Request Object].
* The `Content-Type` header of the response MUST be strictly defined as `application/oauth-authz-req+jwt` [Document: RFC 9101: The OAuth 2.0 Authorization Framework: JWT-Secured Authorization Request (JAR), Chapter: 5.2.3 Authorization Server Fetches Request Object].

**Security & Headers**
* The Request Object URI MUST have appropriate entropy for its lifetime so that the URI is not guessable [Document: RFC 9101: The OAuth 2.0 Authorization Framework: JWT-Secured Authorization Request (JAR), Chapter: 5.2.1 URI Referencing the Request Object].
* For Online Verification, ONLY passing a request object by reference is supported [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5. Authorization Request].

**JSON Schema / Request Body Assertions**
* *Not applicable. As a standard HTTP GET request, no request body is expected or processed.*

**JSON Schema / Response Body Assertions**
* The response schema MUST be defined as a `string` (typically with the format `jwt`) rather than a JSON object, because the Request Object is a JSON Web Token (JWT) that is signed [Document: RFC 9101: The OAuth 2.0 Authorization Framework: JWT-Secured Authorization Request (JAR), Chapter: 4 Request Object].
* *Note on JWT Content Linting:* While OpenAPI defines the response as a string, if the API contract documents the expected JWT payload/headers (e.g. via custom schemas or descriptions), the following Swiss Profile overrides MUST apply and SHOULD be asserted:
    * The JWT header MUST require the parameter `profile_version` to indicate the Swiss Profile version [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5. Authorization Request].
    * The `aud` claim inside the Request Object MUST be defined as strictly `"https://self-issued.me/v2"` because Static Discovery metadata is used [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.8. aud of a Request Object].
    * The JWT payload MUST require the `jwks` parameter [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
    * The JWT payload MUST require the `encrypted_response_enc_values_supported` parameter [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
    * The JWT payload MUST NOT support or expect the `vp_formats_supported` parameter, as the credential format identifier is determined in the DCQL query's format parameter instead [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
    * The JWT payload MUST NOT support or expect the `transaction_data` parameter [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
    * If `verifier_info` is documented, the sub-property `verifier_info.credential_ids` MUST be explicitly NOT SUPPORTED [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 5.1 New Parameters].
    * The API contract MUST NOT document or expect properties like `request` or `request_uri` inside the payload [Document: RFC 9101: The OAuth 2.0 Authorization Framework: JWT-Secured Authorization Request (JAR), Chapter: 4 Request Object].