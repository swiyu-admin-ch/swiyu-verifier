# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.1.0

-    Splitting a POM into a parent POM with two submodules: one for the business service logic and the other for the 
     infrastructure for the API. The module for the business logic now generates a JAR that can also be used for tests 
     or implementations in other projects.

## 1.0.0

-   Initial Release
-   All values for VerificationErrorResponseCode are now in lowercase (previously uppercase).
    If you generate your classes from the API, it is recommended to regenerate them again.
    Otherwise, you can ignore case sensitivity (uppercase/lowercase) in your mapping for VerificationErrorResponseCode.
  