package ch.admin.bj.swiyu.verifier.service.oid4vp.domain.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssuerPublicKeyLoaderTest {

    private IssuerPublicKeyLoader publicKeyLoader;
    private DidResolverFacade mockedDidResolverFacade;

    @BeforeEach
    void setUp() {
        mockedDidResolverFacade = mock(DidResolverFacade.class);
        publicKeyLoader = new IssuerPublicKeyLoader(mockedDidResolverFacade, new ObjectMapper());
    }

    @Test
    void loadPublicKey_throwsException() throws DidSidekicksException {
        // GIVEN (an issuer registered in the DID registry and an issuer signed SD-JWT)
        var issuerDidDocument = DidDocFixtures.issuerDidDocWithMultikey(
                "did:example:123",
                "did:example:123#key-2",
                KeyFixtures.issuerPublicKeyAsMultibaseKey());
        var issuerDidTdw = issuerDidDocument.getId();
        var issuerKeyId = issuerDidDocument.getVerificationMethod().getFirst().getId();
        var fragment = "key-2";

        when(mockedDidResolverFacade.resolveDid(issuerDidTdw, fragment))
                .thenThrow(new DidResolverException("Resolution failed"));

        var error = assertThrows(LoadingPublicKeyOfIssuerFailedException.class,
                () -> publicKeyLoader.loadPublicKey(issuerDidTdw, issuerKeyId));
        assertEquals("Failed to lookup public key from JWT Token for issuer did:example:123 and kid did:example:123#key-2", error.getMessage());
    }

    @Test
    void loadPublicKey_JsonWebKey() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, DidSidekicksException {
        // GIVEN (an issuer registered in the DID registry and an issuer signed SD-JWT)
        var issuerDidDocument = DidDocFixtures.issuerDidDocWithJsonWebKey(
                "did:example:123",
                "did:example:123#key-1",
                KeyFixtures.issuerPublicKeyAsJsonWebKey());
        var issuerDidId = issuerDidDocument.getId();
        var issuerKeyId = issuerDidDocument.getVerificationMethod().getFirst().getId();
        var fragment = "key-1";

        // adapt mock to new resolveDid(did, fragment) API returning Jwk
        when(mockedDidResolverFacade.resolveDid(issuerDidId, fragment))
                .thenReturn(issuerDidDocument.getKey(fragment));

        // WHEN
        var publicKey = publicKeyLoader.loadPublicKey(issuerDidId, issuerKeyId);

        // THEN
        assertThat(publicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(publicKey.getEncoded()).isEqualTo(KeyFixtures.issuerPublicKeyEncoded());
    }

    @Test
    void testLoadPublicKeyWithIssuerFromTdw() throws Exception {
        // given
        String issuerDidTdw = "did:web:tdw.example";
        String issuerKeyId = issuerDidTdw + "#key-1";
        String fragment = "key-1";

        var issuerDidDocument = DidDocFixtures.issuerDidDocWithJsonWebKey(
                issuerDidTdw,
                issuerKeyId,
                KeyFixtures.issuerPublicKeyAsJsonWebKey());

        // adapt mock to new resolveDid(did, fragment) API returning Jwk
        when(mockedDidResolverFacade.resolveDid(issuerDidTdw, fragment))
                .thenReturn(issuerDidDocument.getKey(fragment));

        // WHEN
        var publicKey = publicKeyLoader.loadPublicKey(issuerDidTdw, issuerKeyId);

        // THEN
        assertThat(publicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(publicKey.getEncoded()).isEqualTo(KeyFixtures.issuerPublicKeyEncoded());
    }
}