# Getting started with the SWIYU Generic Verifier (For integration use)

The swiyu Generic Verifier is a web server implementing the technical standards as specified in the ["Swiss Profile Verification"](https://swiyu-admin-ch.github.io/specifications/swiss-profile-verification/). Together with the other generic components provided, this software forms a collection of APIs allowing issuance and verification of verifiable credentials without the need to reimplement the standards. You'll find additional documentation in this [GitHub repository](https://github.com/swiyu-admin-ch/swiyu-verifier).

> [!IMPORTANT]
> Please be advised that the current system and its operations are provided on a best-effort basis and will continue to evolve over time. The security of the system and its overall maturity remain under development.


[![ecosystem components](https://swiyu-admin-ch.github.io/assets/images/components.png)](https://swiyu-admin-ch.github.io/assets/images/components.png)

# Deployment instructions

> [!IMPORTANT]
> Please make sure that you did the following steps before starting the deployment:
>
> - Registered yourself on the swiyu Trust Infrastructure portal
> - Registered yourself on the API self-service portal
> - Generated signing keys e.g. using the `didtoolbox.jar`
> - Generated a DID which is registered on the identifier registry
>
> The required steps are explained in the [Base- and Trust Registry Cookbook](https://swiyu-admin-ch.github.io/cookbooks/onboarding-base-and-trust-registry/)

## Set the environment variables

A sample compose file can be found in [`sample.compose.yml`](https://github.com/swiyu-admin-ch/swiyu-verifier/blob/main/sample.compose.yml) file. You also need to configure a list of environment variables in the `.env` file and adapt the
[verifier metadata](https://github.com/swiyu-admin-ch/swiyu-verifier/blob/main/sample.compose.yml#L40) to your use case.
The metadata information will be provided to the holder on a dedicated endpoint (`/oid4vp/api/openid-client-metadata.json`) serving as metadata information of your verifier.

| Name                          | Description                                                                                                                                                                                                                                                                                                     | Example                                                                                                                                                          |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `EXTERNAL_URL`                | This will be used to build the correct verification_deeplink. You must provide the /oid4vp endpoints there, which must use https-protocol otherwise the wallet will refuse to connect.                                                                                                                          |                                                                                                                                                                  |
| `VERIFIER_DID`                | DID you created during the [onboarding](https://swiyu-admin-ch.github.io/cookbooks/onboarding-base-and-trust-registry/#create-a-did-or-create-the-did-log-you-need-to-continue)                                                                                                                                 | did:tdw:QmejrSkusQgeM6FfA23L6NPoLy3N8aaiV6X5Ysvb47WSj8:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:ff8eb859-6996-4e51-a976-be1ca584c124             |
| `DID_VERIFICATION_METHOD`     | Verification method, which can be taken from the did log response. The Verification Method must match the selected SIGNING_KEY! [onboarding process](https://swiyu-admin-ch.github.io/cookbooks/onboarding-base-and-trust-registry/#create-a-did-or-create-the-did-log-you-need-to-continue) method             | did:tdw:Qmd9bwsodZ1GAz4h8D7Vy6qRio78voXifDrnXokSTsMVQK:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:18fa7c77-9dd1-4e20-a147-fb1bec146085#auth-key-01 |
| `SIGNING_KEY`                 | EC Private key, which can be taken from [onboarding process](https://swiyu-admin-ch.github.io/cookbooks/onboarding-base-and-trust-registry/#create-a-did-or-create-the-did-log-you-need-to-continue) you can use any generated key but it must match the `DID_VERIFICATION_METHOD`                              |
| `OPENID_CLIENT_METADATA_FILE` | Path to the OpenID client metadata file. An example can be found [here](https://github.com/swiyu-admin-ch/swiyu-verifier/blob/main/verifier-application/src/main/resources/client_metadata.json) or in the [sample_compose](https://github.com/swiyu-admin-ch/swiyu-verifier/blob/main/sample.compose.yml#L40). |

Please note that in the default configuration the verifier service is set up in a way to easily gain experience with the verification process, not intended for production use. For additional information on how to securely deploy the swiyu-verifier check out the [Deployment considerations](https://github.com/swiyu-admin-ch/swiyu-verifier/blob/main/README.md#deployment-considerations) in the readme.

The provided images can be used with arm based processors, but they are not optimized.

The latest image is available here:

- [swiyu-verifier](https://github.com/swiyu-admin-ch/swiyu-verifier/pkgs/container/swiyu-verifier)

## Creating a verification

> [!TIP]
> For a detailed understanding of the verification process and the data structure of verification please consult the [Verification Process](https://github.com/swiyu-admin-ch/swiyu-verifier/blob/main/docs/verification_process.md) or the [documentation](https://github.com/swiyu-admin-ch/swiyu-verifier/blob/main/docs/architecture_generic_verifier.pdf).

Once the service is deployed you can create your first verification request.

Below you find an example for a verification request to check the age_over_18 from a [Beta Credential Service (BCS) Credential](https://www.bcs.admin.ch/bcs-web/#/)
The following request can be performed by using the [swagger endpoint](http://localhost:8083/swagger-ui/index.html) for the sample environment.

**Request**
> [!WARNING]
> The example below is only a bare minimum working example.

```bash
curl -X 'POST' 'http://localhost:8083/management/api/verifications' \
  -H "Content-Type: application/json" \
  -d '{
  "accepted_issuer_dids": [
    "did:tdw:QmPEZPhDFR4nEYSFK5bMnvECqdpf1tPTPJuWs9QrMjCumw:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:9a5559f0-b81c-4368-a170-e7b4ae424527"
  ],
  "jwt_secured_authorization_request": true,
  "response_mode": "direct_post",
  "verification_purpose": {
    "scope": "ch.some.test.scope",
    "purpose_name": {
      "default": "Test"
    },
    "purpose_description": {
      "default": "This is a test and this is its description"
    }
  },
  "dcql_query": {
    "credentials": [
      {
        "id": "age_check_dcql",
        "format": "dc+sd-jwt",
        "meta": {
          "vct_values": ["betaid-sdjwt"]
        },
        "claims": [
          {
            "path": ["age_over_18"]
          }
        ],
        "require_cryptographic_holder_binding": true
      }
    ]
  }
}'
```

> [!WARNING]
> ⚙️ Please, store the value of <code>"id"</code> field from the response above into shell variable <code>VERIFICATION_ID</code>, as it is required in the "Get the verification result" call.


> [!NOTE]
>  <strong><code>accepted_issuer_dids</code>:</strong> This field contains a list of DIDs from credential issuers whose credentials your verifier will accept. Replace the <code>${ISSUER_DID}</code> placeholder with the actual DID of your issuer. For quick testing with the Beta Credential Service (BCS) Public Beta, you can use: <code>did:tdw:QmPEZPhDFR4nEYSFK5bMnvECqdpf1tPTPJuWs9QrMjCumw:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:9a5559f0-b81c-4368-a170-e7b4ae424527</code>. Then issue a credential using <a href="https://www.bcs.admin.ch/bcs-web/#/">BCS Public Beta-ID</a> and verify it with your own verifier.

**Response**

The response contains a `"verification_deeplink"` field which points to the verification request, that you have created. To use the link, create a deep-link QR code from the `"verification_deeplink"` response field and scan it with the swiyu-Wallet app.

```json
{
    "id": "4d7c91e9....",
    "request_nonce": "hqxN8V...",
    "state": "SUCCESS",
    "verification_url": "http://localhost:8083/oid4vp/api/request-object/4d7c91e9....",
    "verification_deeplink": "swiyu-verify://?client_id=did%3Aexample%3A12345&request_uri=http%3A%2F%2F%24%7Bserver.host%7D%3A8083%2Foid4vp%2Fapi%2Frequest-object%2F4d7c91e9...."
}
```

## Get the verification result

**Request**

> [!WARNING]
> ⚙️ This is an example for the sample environment. Please, replace the placeholder `${VERIFICATION_ID}` with the actual ID of the verification, or just ensure the shell variable `VERIFICATION_ID` has already been set accordingly.

```bash
curl -X GET \
  -H "Accept: application/json" \
  http://localhost:8083/management/api/verifications/${VERIFICATION_ID}
```

# Testing your instance

We provide a [test application](https://github.com/swiyu-admin-ch/swiyu-generic-application-test) for running end-to-end tests and a [test wallet](https://github.com/swiyu-admin-ch/swiyu-generic-test-wallet) to validate your instance of the generic components.

# Your Feedback?

We would be pleased if you spend about 3 additional minutes and give us feedback on the swiyu Public Beta Trust Infrastructure and your onboarding process! With Public Beta, we want to give ecosystem stakeholders the opportunity to gain initial experience and build their own use cases on the trust infrastructure of the future e-ID. Your [feedback](https://findmind.ch/c/feedback_publicbeta_infr_en) will help us to further develop and improve the touchpoints, and we greatly appreciate your support.