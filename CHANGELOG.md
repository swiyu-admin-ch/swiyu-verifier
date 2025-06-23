# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Latest

### Added

- Optional callback as alternative for active polling for verification status.

### Changed

- Breaking! updated url path to distinguish management and oid4vp urls
    - Management (internal) uri contain /private
    - OID4VP (public) uri contain /public
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

## 1.0.1

### Changed

- Removed incorrect sentence from the README file with mentions of a non-existing env variable CLIENT_ID.
- Now only the env variable VERIFIER_DID is used to set the Verifier DID.

## 1.0.0

- Initial Release
- All values for VerificationErrorResponseCode are now in lowercase (previously uppercase).
  If you generate your classes from the API, it is recommended to regenerate them again.
  Otherwise, you can ignore case sensitivity (uppercase/lowercase) in your mapping for VerificationErrorResponseCode.