# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 2.0.9

### Changed

- Use rewritten url instead of input urls in status list resolver adapter

## 2.0.8

### Changed

- Upgrade DID Resolver library

### Fixed

- Disallow JSONPath filter expressions in validation.

## 2.0.7

### Changed

-   Changed workflow file to fix image build on github

## 2.0.6

### Changed

-   Upgrade did-resolver library from version 1.0.1 to 2.0.0


## 2.0.5

### Added

-   Additional env variable (application.accepted-status-list-hosts) to limit the accepted status list hosts during a verification. The default values are the prod status list and can be overwritten by the environment variable APPLICATION_ACCEPTED_STATUS_LIST_HOSTS.

## 2.0.4

### Changed

- Disabled logging of all actuator requests. The default filter regex pattern is `.*/actuator/.*`. The expression can be
  customized by setting the `request.logging.uri-filter-pattern` property.

## 2.0.3

### Changed

- Add additional legacy client error code client_rejected, because the current swiyu wallet still uses this custom error
  code

## 2.0.2

### Changed

- Changed the referenced oid4vp specification to the correct
  version https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html

## 2.0.1

### Fixed

-   Fixed potential decompression bomb security issue

## 2.0.0

### Changed

- Adapted the error code structure to be aligned with RFC 6749 and limited the possible error codes during the
  presentation submission to the values
  in https://openid.net/specs/openid-4-verifiable-presentations-1_0-20.html#section-6.4

## 1.2.2

### Fixed

-   Limiting allowed issuer of status list to the issuer of the referenced token, as there is currently
    no use case where the issuer of the status list would be different from the issuer of the token.

## 1.2.1

### Changed

-   Provide securosys HSM Primus jce provider (no change necessary for user)

## 1.2.0

### Added

-   Add new acceptable-proof-time-window-seconds settings, checking if the holder's proof has been created in a realistic
    timeframe. It is not recommended to change this value unless you have very high latency or extreme clock skew.

## 1.1.2

### Fixed

-   Changing the status list get process to 1 call instead of 2 with checking the content size

## 1.1.1

### Fixed

-   Updated Spring Boot Parent, fixing CVE-2024-50379
-   Reject VCs which present claims as selective disclosures that MUST NOT be selectively disclosed

## 1.1.0

### Added

-   Extending prometheus export with metrics for build

## 1.0.1

### Added

-   Adding documentation for error codes used

## 1.0.0

-   Initial Release