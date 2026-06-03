# HSM example Dockerfiles

Reference images that layer HSM-backed signing onto the hardened, default
`swiyu-verifier` image (`ghcr.io/swiyu-admin-ch/swiyu-verifier:<tag>`,
`dhi.io`-based, no shell, `nonroot`).

The base image strips most of the C runtime: `libgcc_s.so.1` is re-hydrated by
the project's main `Dockerfile.dhi`, but anything else a vendor PKCS#11
library transitively needs (`libssl`, `libcrypto`, `libgssapi_krb5`, `libkrb5`,
…) must be staged in by the derived image. The examples below do exactly that.

## Files

| File | Use case |
|---|---|
| [`Dockerfile.sunpkcs11`](Dockerfile.sunpkcs11) | `SIGNING_KEY_MANAGEMENT_METHOD=pkcs11` — JDK-bundled SunPKCS11 bridge talking to a vendor PKCS#11 module (`libsofthsm2`, Thales Luna Cryptoki, nCipher, AWS CloudHSM, …). |
| [`Dockerfile.securosys`](Dockerfile.securosys) | Securosys Primus HSM via the vendor JCE provider JAR (`primusX-java11.jar`), configured through the standard `HSM_*` environment variables. |
| [`pkcs11.cfg.example`](pkcs11.cfg.example) | Starting template for the Sun PKCS#11 config file referenced by `HSM_CONFIG_PATH`. |

## Building

Both Dockerfiles take a `VERIFIER_IMAGE` build argument so you can pin them to
the released verifier tag you want to layer onto:

```bash
docker build \
  --build-arg VERIFIER_IMAGE=ghcr.io/swiyu-admin-ch/swiyu-verifier:3.0.0 \
  -f examples/hsm/Dockerfile.sunpkcs11 \
  -t my-org/swiyu-verifier-hsm:3.0.0-sunpkcs11 \
  .

docker build \
  --build-arg VERIFIER_IMAGE=ghcr.io/swiyu-admin-ch/swiyu-verifier:3.0.0 \
  -f examples/hsm/Dockerfile.securosys \
  -t my-org/swiyu-verifier-hsm:3.0.0-securosys \
  .
```

Both files are minimal templates — adapt the vendor library names, transitive
`.so` dependencies, and config-file contents to your specific HSM. The
companion migration guide
[`migration-guides/v2.x-to-v3.0.0.md` §4](../../migration-guides/v2.x-to-v3.0.0.md)
covers the common pitfalls (missing transitive libs, file permissions for the
`nonroot` user, provider registration).
