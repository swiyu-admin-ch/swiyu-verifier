# Verification Validator Service

This software is a web server implementing the technical standards as specified in
the [Swiss E-ID & Trust Infrastructure technical roadmap](https://github.com/e-id-admin/open-source-community/blob/main/tech-roadmap/tech-roadmap.md).
Together with the other generic components provided, this software forms a collection of APIs allowing issuance and
verification of verifiable credentials without the need of reimplementing the standards.

The Generic Verifier Validator Service is the public facing validator to handle validation with the wallet.

As with all the generic issuance & verification services it is expected that every issuer and verifier hosts their own
instance of the service.

The verification validator service is linked to the verification management services through a database, allowing to
scale every service independently of the management service.

## Table of Contents

- [Overview](#Overview)
- [Installation/Building](#installationbuilding)
- [Implementation details](#implementation-details)
- [Contribution](#contribution)
- [License](#license)

## Overview

```mermaid
flowchart TD
    issint[\Verifier Business System\]
    isam(Verifier Management Service)
    isdb[(Postgres)]
    isoi(Verifier Validator Service)
    wallet[Wallet]
    issint ---> isam
    isam ---> isdb
    isoi ---> isdb
    wallet ---> isoi
```

For a general overview over all components, please check [Overview](https://TODO-add-correct-link)

## Installation/Building

- Start application VerifierManagementApplication with local profile
    - Starts docker compose for database
    - Runs Flyway migrations if needed
- After the start api definitions can be found [here](http://localhost:8080/swagger-ui/index.html#/)

## Implementation details

### Environment variables

| Variable          | Description                                                                                     | Type                | Default |
|-------------------|-------------------------------------------------------------------------------------------------|---------------------|---------|
| EXTERNAL_URL      | URL of this deployed instance in order to add it to the request                                 | URL                 | None    |
| VERIFIER_DID      | DID of this service-instance to identify the requester                                          | string (did:tdw)    | none    |
| VERIFIER_NAME     | Client name which is included in the verification request as part of the metadata               | string              | None    |
| VERIFIER_LOGO     | Client logo uri which is included in the verification request as part of the metadata           | string              | None    |
| BBS_KEY_SEED      | Seed to generate the bbs key from [bbs-library](https://github.com/e-id-admin/bbsplus)          | string              | None    |
| SD_JWT_PUBLIC_KEY | Temporary variable to insert the public key for sdjwt -> should be replaced by registries calls | string (pem-format) | none    |
| POSTGRES_USER     | Username to connect to the Issuer Agent Database shared with the issuer agent managment service | string              | none    |
| POSTGRES_PASSWORD | Username to connect to the Issuer Agent Database                                                | string              | none    |
| POSTGRES_URL      | JDBC Connection string to the shared DB                                                         | string              | none    |

## Contribution

We appreciate feedback and contribution. More information can be found in the [CONTRIBUTING-File](/CONTRIBUTING.md).

## License

This project is licensed under the terms of the MIT license. See the [LICENSE](/LICENSE) file for details.
