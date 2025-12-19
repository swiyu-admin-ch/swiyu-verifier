# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## latest

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