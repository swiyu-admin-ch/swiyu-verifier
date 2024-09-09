package ch.admin.bit.eid.oid4vp.fixtures;

import ch.admin.eid.didtoolbox.DidDoc;
import lombok.experimental.UtilityClass;

import static ch.admin.bit.eid.oid4vp.fixtures.KeyFixtures.issuerPublicKeyAsMultibaseKey;

/**
 * Fixtures for did documents, so we can use them in tests.
 */
@UtilityClass
public class DidDocFixtures {
    public static final String DEFAULT_ISSUER_DID_TDW = "did:test:issuer";
    public static final String DEFAULT_METHOD_KEY_ID = DEFAULT_ISSUER_DID_TDW + "#key-1";

    public static DidDoc issuerDidDoc() {
        return issuerDidDocWithMultikey(
                DEFAULT_ISSUER_DID_TDW,
                DEFAULT_METHOD_KEY_ID,
                issuerPublicKeyAsMultibaseKey());
    }

    public static DidDoc issuerDidDoc(String issuerId, String keyId) {
        return issuerDidDocWithMultikey(
                issuerId,
                keyId,
                issuerPublicKeyAsMultibaseKey());
    }

    public static DidDoc issuerDidDocWithJsonWebKey(String didTdw, String keyId, String jsonWekKey) {
        var template = """
                {
                   "@context": [
                     "https://www.w3.org/ns/did/v1",
                     "https://w3id.org/security/multikey/v1"
                   ],
                   "id": "${didTdw}",
                   "controller": ["${didTdw}"],
                   "verificationMethod": [
                     {
                       "id": "${keyId}",
                       "controller": "${didTdw}",
                       "type": "JsonWebKey2020",
                       "publicKeyJwk": ${publicKey}
                     },
                     {
                       "id": "${didTdw}#key-2",
                       "controller": "${didTdw}",
                       "type": "Multikey",
                       "publicKeyMultibase": "u6wGm2Gto9X9z2v04BBXi5KzxCSotFocsFfimoguUzxDpiYGItnnk1pc8oIulbQqXEnkWz7NnDANmoS+6PJoK61T4mK8jvCVxa2ui0pA9D0JBGscWT4O4JPKqmAdid+PxIA4"
                     },
                     {
                       "id": "${didTdw}#key-3",
                       "controller": "${didTdw}",
                       "type": "Multikey",
                       "publicKeyMultibase": "uaUhTY0IzRGl4SnBzckZSVnZqcGxXOTh1NWdTTlBvVEc"
                     }
                   ]
                 }
                """;
        var json = template
                .replace("${didTdw}", didTdw)
                .replace("${keyId}", keyId)
                .replace("${publicKey}", jsonWekKey);
        return DidDoc.Companion.fromJson(json);
    }

    public static DidDoc issuerDidDocWithMultikey(String didTdw, String keyId, String publicKey) {
        var template = """
                {
                   "@context": [
                     "https://www.w3.org/ns/did/v1",
                     "https://w3id.org/security/multikey/v1"
                   ],
                   "id": "${didTdw}",
                   "controller": ["${didTdw}"],
                   "verificationMethod": [
                     {
                       "id": "${keyId}",
                       "controller": "${didTdw}",
                       "type": "Multikey",
                       "publicKeyMultibase": "${publicKey}"
                     },
                     {
                       "id": "${didTdw}#key-2",
                       "controller": "${didTdw}",
                       "type": "Multikey",
                       "publicKeyMultibase": "u6wGm2Gto9X9z2v04BBXi5KzxCSotFocsFfimoguUzxDpiYGItnnk1pc8oIulbQqXEnkWz7NnDANmoS+6PJoK61T4mK8jvCVxa2ui0pA9D0JBGscWT4O4JPKqmAdid+PxIA4"
                     },
                     {
                       "id": "${didTdw}#key-3",
                       "controller": "${didTdw}",
                       "type": "Multikey",
                       "publicKeyMultibase": "uaUhTY0IzRGl4SnBzckZSVnZqcGxXOTh1NWdTTlBvVEc"
                     }
                   ]
                 }
                """;
        var json = template
                .replace("${didTdw}", didTdw)
                .replace("${keyId}", keyId)
                .replace("${publicKey}", publicKey);
        return DidDoc.Companion.fromJson(json);
    }
}
