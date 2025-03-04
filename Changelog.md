# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.2.0

- Add new acceptable-proof-time-window-seconds settings, checking if the holder's proof has been created in a realistic
  timeframe. It is not recommended to change this value unless you have very high latency or extreme clock skew.

## 1.1.1

### Fixed

- Updated Spring Boot Parent, fixing CVE-2024-50379
- Reject VCs which present claims as selective disclosures that MUST NOT be selectively disclosed

## 1.1.0

### Added

- Extending prometheus export with metrics for build

## 1.0.1

### Added

- Adding documentation for error codes used

## 1.0.0

- Initial Release