package ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures;

import ch.admin.eid.did_sidekicks.DidDoc;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import lombok.experimental.UtilityClass;

import static ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures.issuerPublicKeyAsMultibaseKey;

/**
 * Fixtures for did documents, so we can use them in tests.
 */
@UtilityClass
public class DidDocFixtures {
    public static final String DEFAULT_ISSUER_DID_TDW = "did:test:issuer";
    public static final String DEFAULT_METHOD_KEY_ID = DEFAULT_ISSUER_DID_TDW + "#key-1";

    public static DidDoc issuerDidDoc() throws DidSidekicksException {
        return issuerDidDocWithMultikey(
                DEFAULT_ISSUER_DID_TDW,
                DEFAULT_METHOD_KEY_ID,
                issuerPublicKeyAsMultibaseKey());
    }

    public static DidDoc issuerDidDoc(String issuerId, String keyId) throws DidSidekicksException {
        return issuerDidDocWithMultikey(
                issuerId,
                keyId,
                issuerPublicKeyAsMultibaseKey());
    }

    public static DidDoc issuerDidDocWithJsonWebKey(String didWebvh, String keyId, String jsonWekKey) throws DidSidekicksException {
        var template = """
                {
                   "id": "${didWebvh}",
                   "controller": "${didWebvh}",
                   "verificationMethod": [
                     {
                       "id": "${keyId}",
                       "controller": "${didWebvh}",
                       "type": "JsonWebKey2020",
                       "publicKeyJwk": ${publicKey}
                     }
                   ]
                 }
                """;
        var json = template
                .replace("${didWebvh}", didWebvh)
                .replace("${keyId}", keyId)
                .replace("${publicKey}", jsonWekKey);
        return DidDoc.Companion.fromJson(json);
    }

    public static DidDoc issuerDidDocWithMultikey(String didWebvh, String keyId, String publicKey) throws DidSidekicksException {
        var template = """
                {
                   "id": "${didWebvh}",
                   "controller": "${didWebvh}",
                   "verificationMethod": [
                     {
                       "id": "${keyId}",
                       "controller": "${didWebvh}",
                       "type": "Multikey",
                       "publicKeyMultibase": "${publicKey}"
                     },
                     {
                       "id": "${didWebvh}#key-2",
                       "controller": "${didWebvh}",
                       "type": "Multikey",
                       "publicKeyMultibase": "u6wGm2Gto9X9z2v04BBXi5KzxCSotFocsFfimoguUzxDpiYGItnnk1pc8oIulbQqXEnkWz7NnDANmoS+6PJoK61T4mK8jvCVxa2ui0pA9D0JBGscWT4O4JPKqmAdid+PxIA4"
                     },
                     {
                       "id": "${didWebvh}#key-3",
                       "controller": "${didWebvh}",
                       "type": "Multikey",
                       "publicKeyMultibase": "uaUhTY0IzRGl4SnBzckZSVnZqcGxXOTh1NWdTTlBvVEc"
                     }
                   ]
                 }
                """;
        var json = template
                .replace("${didWebvh}", didWebvh)
                .replace("${keyId}", keyId)
                .replace("${publicKey}", publicKey);
        return DidDoc.Companion.fromJson(json);
    }
}