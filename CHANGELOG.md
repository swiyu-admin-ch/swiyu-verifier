# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

# [NEXT]

## Added
- Integrate `pgpverify-maven-plugin` to cryptographically verify PGP signatures of all third-party dependencies during the build. The build fails if an artifact has no signature or an invalid signature. PGP keys are cached in CI/CD to avoid redundant downloads `(#836)`.
- Add additional check if `verification_purpose.purpose_name` and `verification_purpose.purpose_description` contain the necessary default keys

## Removed
- Removed the ` client_id_scheme` from the application configuration as it is no longer used and replaced by the `client_id_prefix` configuration property with default value `decentralized_identifier`, which can be changed or set to null. Therefore, the `client_id` will be `${client_id_prefix}:${client_id}`.

## Fixed
- update casting logic which was fixed in sdjwt library v1.9
- update `DcqlClaimDto` to prevent business verifier to send an empty `values` list (which is not allowed by the spec)
- check `_sd_alg` value in SD-JWT verifier to ensure it is a supported algorithm (currently only `sha-256` is supported)
- Limited the number of vcs sent with the multiple flag to 1 as the flow is not supported yet. The verifier will reject the request if more than 1 vcs are sent.
- State provided in response is now also acceted when using direct_post.jwt as part of the jwt.

## Changed
- oauthState must be sent in verification response otherwise the verifier rejects the response.
- When fetching an expired Verification Management Object, will now return an error instead of returning it one last time
- Uses A256GCM instead of A128GCM for encryption.

# [3.0.2] - 2026-06-12

## Fixed
- update didresolver to 2.8.2

# [3.0.1]

## Fixed
- update Netty version to 4.2.15.Final to address CVE

## [3.0.0]

### Added
- **On-the-Fly vqPS Registration (Trust Protocol 2.0):** The verifier can now automatically register Verification Query Public Statements (vqPS) with the Trust Management System (TMS) when a new verification session is created. This is an optional feature and requires the following new environment variables to be set `(#868)`.
- **New `verification_purpose` field on `POST /management`:** The management API now accepts an optional `verification_purpose` object containing `scope`, `purpose_name` (localized map) and `purpose_description` (localized map). When provided together with `SWIYU_TMS_AUTHORING_URL`, the verifier registers or reuses a vqPS for the given purpose and injects it into the signed Authorization Request sent to the wallet `(#868)`.
- **Trust Protocol 2.0 support**, automatically fetching trust statements from the configured api url SWIYU_TRUST_REGISTRY_API_URL with in-memory caching `(#844, #884)`.
  (Note: Ensure that Trust Protocol 2.0 is supported by the other ecosystem components you use before integrating this feature)
- Support using Trust Protocol 2.0 as source of trust instead of manually maintaining a list of trusted dids.
  This requires setting trust anchors with the correct trust regirsty url to ensure trust statements are not from the wrong source `(#844)`.
- OAuth 2.0 state parameter is now included in request object. Will not be enforced yet to allow wallet to adopt it
- Added possibility to configure the `maxCompressedCipherTextLength` for the JWE encryption of the VP response. The default value is 100000, which is sufficient for most cases and should only be changed in very exclusive scenarios.
- **Recursive SD-JWT disclosure** resolution and validation:
  - Resolve nested `_sd` entries in objects and arrays, inserting claim name/value pairs from disclosures at the `_sd` level and recursively processing the inserted values `(#696)`.
- Added `Swiss Government Root CA VI` to image `(#683)`.
- All four runtime health checks can now be individually disabled via configuration (see `management.health.*` properties) `(#949)`:
  - `SIGNING_KEY_VERIFICATION_ENABLED` – disables the signing-key verification check (default: `true`). Set to `false` when using dynamic key management without a statically configured `DID_VERIFICATION_METHOD`. When disabled, the check reports `UP` with detail `signingKeyVerificationMethod: disabled`. The check also automatically reports `UP` (detail: `not configured`) when `DID_VERIFICATION_METHOD` is empty.
  - `CALLBACK_HEALTH_ENABLED` – disables the stale-callback check (default: `true`).
  - `STATUS_REGISTRY_HEALTH_ENABLED` – disables the status-registry accessibility check (default: `true`).
  - `IDENTIFIER_REGISTRY_HEALTH_ENABLED` – disables the identifier-registry DID-resolution check (default: `true`).

### Fixed
- Allow hsm key id and key pin to be overridden individually `(#947)`.
- Remove $.client_metadata.client_id, it is an unsued remnant from an earlier version which was still required to be set `(#951)`.
- Fixed a TOCTOU race condition on the VP response endpoint (`direct_post`, `direct_post.jwt`) that allowed two concurrent wallet submissions for the same session to both be accepted, leading to non-deterministic verification results `(#962)`.
- Fixed JWK parsing for JWK sets with prepended issuer DID `(#978)`.
- Fixed permanent override behaviour for HSM configuration override `(#977)`.
- Fixed DCQL number handling and access modifiers in DCQL processing `(#879)`.
- Fixed handling of duplicated digests in SD-JWT verifier `(#883)`.
- Fixed null values being included in DCQL-DTOs `(#619)`.
- Fixed DCQL `multiple=false` default behaviour `(#800)`.
- Fixed cache condition in `StatusListResolverAdapter`.
- Fixed CA certificate handling in Dockerfile and entrypoint `(#683)`.

### Changed
- Documentation and examples updated to use `dc+sd-jwt` as the canonical SD-JWT VC media type
  (per draft-ietf-oauth-sd-jwt-vc-09 §A.2.1). The verifier continues to accept `vc+sd-jwt`
  on the credential `typ` header during the migration window `(#178)`.
- Renamed the configuration property `application.accepted-status-list-hosts` to
  `application.accepted-registry-hosts`.
- **Docker image:** the published image is now hardened. The default
  (unsuffixed) tag `ghcr.io/swiyu-admin-ch/swiyu-verifier:<tag>` builds from
  `dhi.io/eclipse-temurin:21-debian13`, runs as the pre-configured `nonroot` user
  and contains no shell. During a transition period the previous UBI-based image
  remains available under the `-unhardened` suffix
  (`ghcr.io/swiyu-admin-ch/swiyu-verifier:<tag>-unhardened`). Operators who cannot
  immediately adopt the hardened runtime **must pin to the `-unhardened` tag** until
  they have completed the migration steps in
  [`migration-guides/v2.x-to-v3.0.0.md`](migration-guides/v2.x-to-v3.0.0.md);
  the `-unhardened` variant will be removed in a later release. `(#834)`.
- **Migrated to Spring Boot 4.0.6** (Spring Framework 7) `(#537)`:
  - Upgraded Spring Cloud to 2025.1.1 and springdoc-openapi to 3.0.0.
  - Added dedicated starters for extracted autoconfiguration modules: `spring-boot-starter-webclient`,
    `spring-boot-starter-flyway`, `spring-boot-jackson2`, and `spring-boot-health`.
  - Retained Jackson 2 (`com.fasterxml.jackson`) via `spring-boot-jackson2` and
    `spring.http.converters.preferred-json-mapper=jackson2` (Boot 4 defaults to Jackson 3).
  - Replaced `HttpStatus` with `HttpStatusCode` in `ApiErrorDto` and `StatusListResolverAdapter` to support
    non-standard HTTP status codes no longer present in the `HttpStatus` enum in Spring Framework 7.
  - Removed `@Configuration` from `@ConfigurationProperties` classes (`CacheProperties`, `UrlRewriteProperties`,
    `VerificationProperties`, `ApplicationProperties`) to prevent CGLIB proxy conflicts with Lombok.
  - Removed `CacheManagerCustomizer` implementation from `CacheCustomizer` (API removed in Spring Boot 4);
    cache names are now configured directly in `CachingConfig`.
  - Migrated health contributor imports to `org.springframework.boot.health.contributor`.
  - Upgraded Testcontainers to 2.0 (module artifacts renamed, e.g. `junit-jupiter` → `testcontainers-junit-jupiter`).
  - Replaced `@Mock` with `@MockitoBean` for Spring-managed beans in `@SpringBootTest` integration tests.

### Removed
- fabric8 dependency is removed due to incompatibility with spring boot 4. External configurations are now can still be used with the techniques described in https://docs.spring.io/spring-boot/reference/features/external-config.html For example using `spring.config.import` `(#970)`.
- **Dropped support for unsigned request objects** (not supported by profile anymore) `(#970)`.
- **Dropped support for SWIYU-API-Version 1**, which was using DIF Presentation Exchange. Now only DCQL can be used for verification as defined in OID4VP 1.0 `(#231)`.


## 2.3.0

### Added
- New endpoint `/actuator/env` to retrieve configuration details. This includes an actuator sanitizer and additional properties `(#433)`.
- Added health checks for `(#268)`:
    - Stale callbacks.
    - Status list (checks if endpoints listed in `management.endpoint.health.serviceRegistries` are reachable).
    - Status registry (resolves the DIDs listed in `management.endpoint.health.identifierRegistries`).
- Allow setting the used Database Schema with environment variable `POSTGRES_DB_SCHEMA` (default remains `public`) `(#604)`.
- Include Swiss Profile version indication in JWT-Secured Authorization Requests (JAR): added `profile_version` to the JWT header (swiss-profile-verification) `(#694)`.

### Fixed
- Fixed trust statement retrieval by setting the correct URL `(#535)`.
- Fixed DID resolving to support key fragment resolution and enhanced caching for JWKs instead of full DID documents `(#693)`.
- Allowed metadata access to the database by removing the database-migration init container `(#604)`.
- Added missing `ECDH-ES` algorithm claim to ephemeral JWKs `(#669)`.
- Added validation for `keyId` and `keyPin` presence when creating signer providers to prevent inconsistent input `(#498)`.
- Fixed VP token handling and trust validation, specifically for tokens ending with multiple tilde characters `(#371)`.

### Changed
- Removed the `version` property (value "1.0") from the Authorization Request and verifier metadata `(#694)`.
- Replaced RestClient with WebClient for outbound HTTP requests `(#363)`.
- Disabled the status list cache `(#401)`.
- Improved the error message on the `response-data` endpoint when using `direct_post.jwt` `(#260)`.
- Added a default value for `encrypted_response_enc_values_supported` to reduce ambiguity `(#663)`.
- Added default value fallback methods for `ConfigurationOverride` fields `(#564)`.

## 2.2.0

### Added

### Changed

- Breaking! Either `accepted_issuer_dids` or `trust_anchors must contain` a value. The list itself cannot be empty, as this would implicate that nothing is trusted.
  This is to improve security by avoiding misconfigurations that would lead to accepting any issuer.
- Status list resolving does no longer accept http urls for status lists. Only https urls are allowed now.
- Status Lists are no longer cached by default

## 2.1.0

### Added

- Updated ApiErrorDto and reused it for every error response. This allows for a more consistent error
  response structure. Possible breaking change could be that the `error_code` will be moved to details
- Added WebhookCallbackDto to openapi config schemas.
- Base functionality for DCQL, allowing using OID4VP v1 style along side legacy DIF PE to query credentials. 
  Verifiable presentations are validated and checked according to DCQL "credentials" query. 
  Currently only single credential submissions are supported. To maintain backwards compatibility with old wallet versions using DIF PE remains mandatory.
- Optional End2End encryption with JWE according to OID4VP 1.0. Default is currently still unencrypted to allow wallets to start supporting it. 
  Usage be chosen on verification request basis with new `response_mode` json attribute.  
- Updated didresolver dependency from 2.1.3 to 2.3.0


### Changed
- Allow both vc+sd-jwt (SD JWT VC Draft 05 and older) dc+sd-jwt (SD JWT VC Draft 06 and newer) for presented VC format 

## 2.0.0

### Added

- Optional callback as alternative for active polling for verification status.
- Optional OAuth security with bearer tokens on `/management` endpoints.
  It can be activated and configured via spring environment variables.
- 
### Changed

- client_metadata must now contain a vp_formats field otherwise the app will not start. A valid metadata example is:
```json
{
    "client_id": "your verifier did",
    "version": "1.0.0",
    "vp_formats": {
        "jwt_vp": {
            "alg": [
                "ES256"
            ]
        }
    }
}
```
- Breaking! updated url path to distinguish management (with `/management`) and oid4vp (with `/oid4vp`) urls
- Expanded the verificationClientErrorDto to allow `access_denied` code (the `client_rejected` code is still supported)
- Expanded `ManagementResponseDto` with an additional field `verification_deeplink` with a standard conform
  Authorization Request that can be presented to the wallet as qr-code. The default value for the deeplink schema is set
  to `swiyu-verify` but can be changed in the app-config.
- Splitting a POM into a parent POM with two submodules: one for the business service logic and the other for the
  infrastructure for the API. The module for the business logic now generates a JAR that can also be used for tests
  or implementations in other projects.
- Expanded verification endpoint to allow both the legacy cnf and the correct cnf format. Example of new cnf:

```json
{
    "cnf": {
        "jwk": {
            "kty": "EC",
            "crv": "P-256",
            "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
            "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
        }
    }
}
```

- Using new `jwt_premature` error code when receiving a presentation where the `nbf` time has not yet been reached instead of `malformed_credential` 

## 1.0.1

### Changed

- Removed incorrect sentence from the README file with mentions of a non-existing env variable CLIENT_ID.
- Now only the env variable VERIFIER_DID is used to set the Verifier DID.

## 1.0.0

- Initial Release
- All values for VerificationErrorResponseCode are now in lowercase (previously uppercase).
  If you generate your classes from the API, it is recommended to regenerate them again.
  Otherwise, you can ignore case sensitivity (uppercase/lowercase) in your mapping for VerificationErrorResponseCode.