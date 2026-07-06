### ResponseDataEndpointSpez.md

**Static Compliance Check (AI Swagger Linting) - OID4VP / Swiss Profile**

#### Endpoint: `POST /oid4vp/api/request-object/{request_id}/response-data`

This endpoint acts as the `response_uri` to which the Wallet submits the Authorization Response (which contains the Verifiable Presentation). Because the Swiss Profile strictly enforces the response mode `direct_post.jwt` alongside mandatory encryption, the payload will be delivered as an encrypted JSON Web Token (JWE) within a URL-encoded form body.

**HTTP Behavior & Content-Type**
* The endpoint MUST handle HTTP POST requests [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode "direct_post"].
* The endpoint MUST strictly accept requests with the `Content-Type` defined as `application/x-www-form-urlencoded` [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode "direct_post"].
* A successful response MUST return the HTTP Status `200 OK` [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode "direct_post"].
* The `Content-Type` header of the returned response MUST be strictly defined as `application/json` [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode "direct_post"].

**JSON Schema / Request Body Assertions**
* The request body schema (for `application/x-www-form-urlencoded`) MUST define the `response` property, which encapsulates the encrypted JWT [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response].
* The `response` property MUST be declared as REQUIRED, as the Response Mode `direct_post.jwt` encapsulates all parameters within this single token [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response].
* The `response` property MUST be defined as a `string` (since it holds the base64-encoded JWE representation) [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8. Response].
* The API contract MUST NOT document or expect properties related to Transaction Data, as Transaction Data is explicitly NOT SUPPORTED and SHOULD NOT be used [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.4 Transaction Data].

**JSON Schema / Response Body Assertions (Response back to Wallet)**
* The response body schema for `200 OK` MUST be defined as a valid JSON object [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode "direct_post"].
* The response body schema for `200 OK` MAY define an OPTIONAL `redirect_uri` property of type string to instruct the Wallet where to redirect the user next [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode "direct_post"].
* The endpoint SHOULD define an HTTP `400 Bad Request` response to handle validation or processing failures [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode "direct_post"].
* The error response schema (`400 Bad Request`) MUST be a JSON object containing an `error` property of type string [Document: OpenID for Verifiable Presentations 1.0, Chapter: 8.2. Response Mode "direct_post"].
* Authorization Error Responses SHOULD never be encrypted, meaning the error response schema MUST remain standard JSON and MUST NOT be defined as a JWE string [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.5 Error Response].
* If specific Wallet error codes are documented in the API contract (e.g., as an enum description for the `error` property), the values `invalid_request_uri_method`, `invalid_transaction_data`, `vp_formats_not_supported`, and `wallet_unavailable` MUST NOT be included, as they are explicitly NOT SUPPORTED [Document: Swiss Profile Verification - swiyu technical documentation, Chapter: 8.5 Error Response].
