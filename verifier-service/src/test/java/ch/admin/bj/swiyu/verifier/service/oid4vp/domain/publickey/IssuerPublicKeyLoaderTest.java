package ch.admin.bj.swiyu.verifier.service.oid4vp.domain.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.service.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.eid.did_sidekicks.DidDoc;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader.TRUST_STATEMENT_ISSUANCE_ENDPOINT;
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
        try (DidDoc issuerDidDocument = DidDocFixtures.issuerDidDocWithMultikey(
                "did:example:123",
                "did:example:123#key-2",
                KeyFixtures.issuerPublicKeyAsMultibaseKey())) {

            var issuerDidTdw = issuerDidDocument.getId();
            var issuerKeyId = issuerDidDocument.getVerificationMethod().getFirst().getId();
            var fragment = "key-2";

            when(mockedDidResolverFacade.resolveDid(issuerDidTdw, fragment))
                    .thenThrow(new DidResolverException("Resolution failed"));

            var error = assertThrows(LoadingPublicKeyOfIssuerFailedException.class,
                    () -> publicKeyLoader.loadPublicKey(issuerDidTdw, issuerKeyId));
            assertEquals("Failed to lookup public key from JWT Token for issuer did:example:123 and kid did:example:123#key-2", error.getMessage());
        }
    }

    @Test
    void loadPublicKey_JsonWebKey() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, DidSidekicksException {
        // GIVEN (an issuer registered in the DID registry and an issuer signed SD-JWT)
        try (DidDoc issuerDidDocument = DidDocFixtures.issuerDidDocWithJsonWebKey(
                "did:example:123",
                "did:example:123#key-1",
                KeyFixtures.issuerPublicKeyAsJsonWebKey())) {

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
    }

    @Test
    void testLoadPublicKeyWithIssuerFromTdw() throws Exception {
        // given
        String issuerDidTdw = "did:web:tdw.example";
        String issuerKeyId = issuerDidTdw + "#key-1";
        String fragment = "key-1";

        try(DidDoc issuerDidDocument = DidDocFixtures.issuerDidDocWithJsonWebKey(
                issuerDidTdw,
                issuerKeyId,
                KeyFixtures.issuerPublicKeyAsJsonWebKey())) {

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

    @Test
    void loadPublicKey_whenResolverReturnsNull_throwsLoadingPublicKeyOfIssuerFailedException() throws DidSidekicksException {
        // GIVEN
        String issuerDid = "did:example:456";
        String issuerKeyId = issuerDid + "#key-1";
        String fragment = "key-1";

        when(mockedDidResolverFacade.resolveDid(issuerDid, fragment)).thenReturn(null);

        // WHEN / THEN
        assertThrows(LoadingPublicKeyOfIssuerFailedException.class,
                () -> publicKeyLoader.loadPublicKey(issuerDid, issuerKeyId));
    }

    @Test
    void loadPublicKey_whenKidHasNoFragment_throwsLoadingPublicKeyOfIssuerFailedException() {
        // kid without '#'
        String issuerDid = "did:example:789";
        String malformedKid = "did:example:789key-1";

        assertThrows(LoadingPublicKeyOfIssuerFailedException.class,
                () -> publicKeyLoader.loadPublicKey(issuerDid, malformedKid));
    }

    @Test
    void loadTrustStatement_parsesListFromJson() throws JsonProcessingException {
        String trustRegistryUri = "https://registry.example";
        String vct = "vct-1";
        List<String> expectedStatements = List.of("jwt-one", "jwt-two");
        String expectedUri = trustRegistryUri + TRUST_STATEMENT_ISSUANCE_ENDPOINT;

        when(mockedDidResolverFacade.resolveTrustStatement(expectedUri, vct))
                .thenReturn("[\"%s\", \"%s\"]".formatted(expectedStatements.getFirst(), expectedStatements.get(1)));

        var statements = publicKeyLoader.loadTrustStatement(trustRegistryUri, vct);

        assertThat(statements).isNotNull();
        assertEquals(2, statements.size());
        assertEquals(expectedStatements.getFirst(), statements.getFirst());
        assertEquals(expectedStatements.get(1), statements.get(1));
    }
}