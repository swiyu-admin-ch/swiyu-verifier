# Trusted CA certificates

Every `*.crt` file in this directory is baked into the published Docker image
during build time and added to **both** trust stores:

1. **Java cacerts** (`$JAVA_HOME/lib/security/cacerts`) — used by the JDK TLS
   stack (`RestClient`, `WebClient` with the JDK SSL engine, plain HTTPS).
2. **OS CA bundle** (`/etc/ssl/certs/ca-certificates.crt`) — used by tools
   and libraries that read the system store (Netty native OpenSSL, etc.).

The alias used for each certificate in the Java keystore is the file name
without the `.crt` extension (e.g. `root_ca_vi.crt` → alias `root_ca_vi`).

See [`Dockerfile.dhi`](../Dockerfile.dhi) for the import logic (stages
`ca-bundle` and `java-cacerts`).

## Certificates currently included

| File | Subject | Use case | Source |
|---|---|---|---|
| `root_ca_vi.crt` | `Swiss Government Root CA VI` | Public BIT/admin.ch endpoints (Trust Registry, Status Registry, …) | [bit.admin.ch — Swiss Government Root CA VI](https://www.bit.admin.ch/de/sg-pki-swiss-government-root-ca-vi-de) |
| `swiss_gov_e_root01.crt` | `SwissGovernment-E-Root01` | BIT **intranet** endpoints (`*-intra.api.admin.ch`, e.g. `keymanager-npr-intra`) — required for deployments inside the BIT network | [pki.admin.ch](https://www.bit.admin.ch/) |

### Fingerprints (SHA-256)

Verify the integrity of the files after download with
`openssl x509 -in <file> -noout -fingerprint -sha256`:

- `swiss_gov_e_root01.crt` →
  `75:7B:32:8A:FC:F1:CA:4D:56:7F:87:6A:5B:0D:1D:A1:30:03:76:4D:EF:CF:96:73:14:F0:B1:DE:7C:7B:93:FA`

## Adding a new CA

1. Download the PEM-encoded certificate from the official source.
2. Verify the SHA-256 fingerprint against the publisher's website.
3. Save it under `certs/<descriptive-name>.crt`.
4. Add a row to the table above (subject, use case, source) and append the
   fingerprint in the section above.
5. Rebuild the image — no `Dockerfile` change required.

> **Security note:** Only root CAs you genuinely need to trust for outbound
> HTTPS calls belong here. Each entry widens the trust surface of the image.
