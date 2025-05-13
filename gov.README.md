<!--
SPDX-FileCopyrightText: 2025 Swiss Confederation

SPDX-License-Identifier: MIT
-->

## Gov usage

### 1. Setup up infrastructure

When deployed in an RHOS setup the issuer-management / issuer-agent setup need the following setup

#### Database

Single postgresql databse service needs to be available. Make sure that the following bindings exist between your
database and the application namespace:

- database -> issuer-verifier-management: Full
- database -> issuer-verifier-oid4vci: Read-Write

#### MAV

The MAV needs to be bound to the application namespace. Make sure the secrets are located in the path *
*default/application_secrets**
and you configured the vault so that it uses the application_secrets as properties

```yaml
vaultsecrets:
    vaultserver: https://my-vault-server.example.com
    serviceaccount: default
    cluster: xyz-cluster
    path: default
    properties:
        - application_secrets
``` 

### 2. Set the environment variables

Due to the separation of the secret and non-secret variables the location is split. Make sure that you've set at least
the following variables.
Concerning the actual values take a look at the [sample.compose.yml](sample.compose.yml)

> **After this** continue with [creating an initial verification](README.md#2-creating-a-verification)

| Location                | issuer-agent-management | issuer-agent-oid4vci                                      |
|-------------------------|-------------------------|-----------------------------------------------------------|
| GitOps                  | OID4VP_URL              | EXTERNAL_URL<br/>VERIFIER_DID<br/>DID_VERIFICATION_METHOD |
| ManagedApplicationVault |                         | SIGNING_KEY                                               |