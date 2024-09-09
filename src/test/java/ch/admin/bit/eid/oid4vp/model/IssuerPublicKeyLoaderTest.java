package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.exception.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bit.eid.oid4vp.fixtures.DidDocFixtures;
import ch.admin.bit.eid.oid4vp.fixtures.KeyFixtures;
import ch.admin.bit.eid.oid4vp.mock.SDJWTCredentialMock;
import ch.admin.bit.eid.oid4vp.model.did.DidResolverAdapter;
import ch.admin.eid.didresolver.DidResolveException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssuerPublicKeyLoaderTest {
    private IssuerPublicKeyLoader publicKeyLoader;
    private DidResolverAdapter mockedDidResolverAdapter;

    @BeforeEach
    void setUp() {
        mockedDidResolverAdapter = mock(DidResolverAdapter.class);
        publicKeyLoader = new IssuerPublicKeyLoader(mockedDidResolverAdapter, new ObjectMapper());
    }

    @Test
    void loadPublicKey_MultibaseKey() throws LoadingPublicKeyOfIssuerFailedException, DidResolveException, JOSEException {
        // GIVEN (an issuer registered in the DID registry and an issuer signed SD-JWT)
        var issuerDidDocument = DidDocFixtures.issuerDidDocWithMultikey(
                "did:example:123",
                "did:example:123#key-1",
                KeyFixtures.issuerPublicKeyAsMultibaseKey());
        var issuerDidTdw = issuerDidDocument.getId();
        var issuerKeyId = issuerDidDocument.getVerificationMethod().getFirst().getId();
        when(mockedDidResolverAdapter.resolveDid(issuerDidTdw)).thenReturn(issuerDidDocument);

        var sdjwt = sdjwt(issuerDidDocument.getId(), issuerKeyId);

        // WHEN
        var publicKey = publicKeyLoader.loadPublicKey(sdjwt);

        // THEN
        assertThat(publicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(publicKey.getEncoded()).isEqualTo(KeyFixtures.issuerPublicKeyEncoded());
    }

    @Disabled("Disabled due to EID-1788. DID resolver library sends an invalid JWK (missing y coordinate)")
    @Test
    void loadPublicKey_JsonWebKey() throws LoadingPublicKeyOfIssuerFailedException, DidResolveException, JOSEException {
        // GIVEN (an issuer registered in the DID registry and an issuer signed SD-JWT)
        var issuerDidDocument = DidDocFixtures.issuerDidDocWithJsonWebKey(
                "did:example:123",
                "did:example:123#key-1",
                KeyFixtures.issuerPublicKeyAsJsonWebKey());
        var issuerDidTdw = issuerDidDocument.getId();
        var issuerKeyId = issuerDidDocument.getVerificationMethod().getFirst().getId();
        when(mockedDidResolverAdapter.resolveDid(issuerDidTdw)).thenReturn(issuerDidDocument);

        var sdjwt = sdjwt(issuerDidDocument.getId(), issuerKeyId);

        // WHEN
        var publicKey = publicKeyLoader.loadPublicKey(sdjwt);

        // THEN
        assertThat(publicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(publicKey.getEncoded()).isEqualTo(KeyFixtures.issuerPublicKeyEncoded());
    }

    public static String sdjwt(String issuer, String kidHeader) {
        return new SDJWTCredentialMock(issuer, kidHeader).createSDJWTMock();
    }


}