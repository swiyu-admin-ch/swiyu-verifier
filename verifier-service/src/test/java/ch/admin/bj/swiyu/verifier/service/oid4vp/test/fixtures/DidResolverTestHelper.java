package ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.mock.SDJWTCredentialMock;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.eid.did_sidekicks.DidDoc;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import com.nimbusds.jose.jwk.JWK;

import static org.mockito.Mockito.when;

/**
 * Stubs a mocked {@link DidResolverFacade} so that DID and key resolution can be exercised in tests without any
 * network access.
 * <p>
 * Two kinds of stubs are available:
 * </p>
 * <ul>
 *   <li>{@link #stubResolveKey(DidResolverFacade, SDJWTCredentialMock)} for SD-JWT based tests, which stubs the key
 *       referenced by the {@code kid} header of the mocked credential</li>
 *   <li>the granular {@code stubResolveKey}, {@code stubResolveDid} and {@code stubResolve*Failure} methods, which
 *       stub a single resolution or its failure for an explicitly given key id or DID</li>
 * </ul>
 * <p>
 * All defaults are built from the issuer key of {@link KeyFixtures} and the issuer DID document of
 * {@link DidDocFixtures}, so a stubbed DID document and the key referenced by it belong together. Failures while
 * building a fixture are wrapped in an {@link AssertionError}, so callers do not need to handle checked exceptions.
 * </p>
 * <p>
 * The facade passed in must be a Mockito mock (e.g. {@code @Mock DidResolverFacade} or
 * {@code mock(DidResolverFacade.class)}).
 * </p>
 */
public final class DidResolverTestHelper {

    /**
     * Stubs the key resolution of the given facade for the verification method referenced by the {@code kid} header
     * of the given SD-JWT mock, returning the issuer public key of {@link KeyFixtures}.
     * <p>
     * Convenience for tests that verify a credential created by {@link SDJWTCredentialMock} and therefore do not want
     * to deal with the key id explicitly. The stub is registered via
     * {@link #stubResolveKey(DidResolverFacade, String, JWK)}, using
     * {@link SDJWTCredentialMock#getKidHeaderValue()} as key id.
     * </p>
     *
     * @param didResolverFacade the mocked facade to stub
     * @param sdjwt             the credential mock whose {@link SDJWTCredentialMock#getKidHeaderValue() kid} identifies
     *                          the key to stub
     * @see #stubResolveKey(DidResolverFacade, String)
     * @see #stubResolveKey(DidResolverFacade, String, JWK)
     */
    public static void stubResolveKey(DidResolverFacade didResolverFacade, SDJWTCredentialMock sdjwt) {
        try {
            // Parse the JSON Web Key string into a Nimbus JWK object to ensure correct type
            JWK nimbusJwk = JWK.parse(KeyFixtures.issuerPublicKeyAsJsonWebKey());
            stubResolveKey(didResolverFacade, sdjwt.getKidHeaderValue(), nimbusJwk);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Stubs the key resolution of the given facade to return the issuer public key of {@link KeyFixtures}.
     *
     * @param didResolverFacade the mocked facade to stub
     * @param keyId             the full verification method id (DID including the {@code #fragment})
     * @see #stubResolveKey(DidResolverFacade, String, JWK)
     */
    public static void stubResolveKey(DidResolverFacade didResolverFacade, String keyId) {
        stubResolveKey(didResolverFacade, keyId, KeyFixtures.issuerKey().toPublicJWK());
    }

    /**
     * Stubs the key resolution of the given facade to return the provided key.
     *
     * @param didResolverFacade the mocked facade to stub
     * @param keyId             the full verification method id (DID including the {@code #fragment})
     * @param jwk               the key the resolution should return
     */
    public static void stubResolveKey(DidResolverFacade didResolverFacade, String keyId, JWK jwk) {
        when(didResolverFacade.resolveKey(keyId)).thenReturn(jwk);
    }

    /**
     * Stubs the key resolution of the given facade to fail with a {@link DidResolverException} whose message contains
     * the given key id.
     *
     * @param didResolverFacade the mocked facade to stub
     * @param keyId             the full verification method id (DID including the {@code #fragment})
     */
    public static void stubResolveKeyFailure(DidResolverFacade didResolverFacade, String keyId) {
        when(didResolverFacade.resolveKey(keyId))
                .thenThrow(new DidResolverException("DID resolution failed for " + keyId));
    }

    /**
     * Stubs the DID resolution of the given facade to return a DID document for {@code didId} which carries the
     * issuer key of {@link KeyFixtures} as verification method {@code <didId>#key-1}.
     * <p>
     * The document is built by {@link DidDocFixtures#issuerDidDoc(String, String)}, so its id matches the resolved
     * DID and the verification method id follows the usual {@code <did>#key-1} convention.
     * </p>
     *
     * @param didResolverFacade the mocked facade to stub
     * @param didId             the id of the DID document, without a {@code #fragment}
     * @see #stubResolveDid(DidResolverFacade, String, DidDoc)
     */
    public static void stubResolveDid(DidResolverFacade didResolverFacade, String didId) {
        stubResolveDid(didResolverFacade, didId, defaultDidDoc(didId));
    }

    /**
     * Stubs the DID resolution of the given facade to return the provided DID document.
     * <p>
     * Use this overload when the document has to differ from the default one, e.g. because it carries another key
     * or several verification methods. The checked {@link DidSidekicksException} declared by
     * {@link DidResolverFacade#resolveDid(String)} never occurs for a mock, so it is wrapped in an
     * {@link AssertionError}.
     * </p>
     *
     * @param didResolverFacade the mocked facade to stub
     * @param didId             the id of the DID document, without a {@code #fragment}
     * @param didDoc            the DID document the resolution should return
     * @see #stubResolveDid(DidResolverFacade, String)
     */
    public static void stubResolveDid(DidResolverFacade didResolverFacade, String didId, DidDoc didDoc) {
        try {
            when(didResolverFacade.resolveDid(didId)).thenReturn(didDoc);
        } catch (DidSidekicksException e) {
            throw new AssertionError("Failed to stub DID resolution for " + didId, e);
        }
    }

    /**
     * Stubs the DID resolution of the given facade to fail with a {@link DidResolverException} whose message contains
     * the given DID. The checked {@link DidSidekicksException} declared by
     * {@link DidResolverFacade#resolveDid(String)} never occurs for a mock, so it is wrapped in an
     * {@link AssertionError}.
     *
     * @param didResolverFacade the mocked facade to stub
     * @param didId             the id of the DID document, without a {@code #fragment}
     */
    public static void stubResolveDidFailure(DidResolverFacade didResolverFacade, String didId) {
        try {
            when(didResolverFacade.resolveDid(didId))
                    .thenThrow(new DidResolverException("DID resolution failed for " + didId));
        } catch (DidSidekicksException e) {
            throw new AssertionError("Failed to stub DID resolution for " + didId, e);
        }
    }

    private static DidDoc defaultDidDoc(String didId) {
        try {
            return DidDocFixtures.issuerDidDoc(didId, didId + "#key-1");
        } catch (DidSidekicksException e) {
            throw new AssertionError("Failed to build DID document fixture for " + didId, e);
        }
    }
}