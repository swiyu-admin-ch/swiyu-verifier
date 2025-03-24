# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.1.5

### Added

- Introduced new env variable `STAGE` to set the profiles in the entrypoint file. The value can be set to
  `cloud` to get logs in json format.

## 1.1.4

### Changed

- Adapted error codes to the ones defined in the verifier-agent-oid4vp project.

## 1.1.3

### Changed

- Disabled logging of all actuator requests. The default filter regex pattern is `.*/actuator/.*`. The expression can be
  customized by setting the `request.logging.uri-filter-pattern` property.

## 1.1.2

## Removed

- Deprecated Endpoints without version number are removed

## 1.1.1

- Updated Spring Boot Parent, fixing CVE-2024-50379

## 1.1.0

### Added

- Extending prometheus export with metrics for build

## 1.0.1

- Adding documentation for error codes used

## 1.0.0

- Initial Release