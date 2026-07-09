package ch.admin.bj.swiyu.verifier.service.oid4vp.domain.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverFacade;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerDataLoader;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import ch.admin.eid.did_sidekicks.Jwk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.admin.bj.swiyu.verifier.service.publickey.IssuerDataLoader.TRUST_STATEMENT_ISSUANCE_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssuerDataLoaderTest {

    private IssuerDataLoader publicKeyLoader;
    private DidResolverFacade mockedDidResolverFacade;

    @BeforeEach
    void setUp() {
        mockedDidResolverFacade = mock(DidResolverFacade.class);
        publicKeyLoader = new IssuerDataLoader(mockedDidResolverFacade, new ObjectMapper());
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

    @Test
    void loadPublicDidKey_keyIdModification() throws JOSEException, DidResolverException, DidSidekicksException {
        final String KEY_ID = "key-1";
        final String DID = "did:example";
        var testKey = new ECKeyGenerator(Curve.P_256).keyID(KEY_ID).algorithm(JWSAlgorithm.ES256).generate();
        var testKeyJwk = new Jwk(
            testKey.getAlgorithm().toString(),
            testKey.getKeyID(),
            testKey.getKeyType().toString(),
            testKey.getCurve().toString(),
            testKey.toPublicJWK().getX().toString(),
            testKey.toPublicJWK().getY().toString());
        when(mockedDidResolverFacade.resolveDid(DID, KEY_ID)).thenReturn(testKeyJwk);
        var loadedKey = assertDoesNotThrow(() -> publicKeyLoader.loadJWK(DID, DID + "#" + KEY_ID));
        // Evaluate that the key performs the same way
        var jwtKid = DID + "#" + KEY_ID;
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(jwtKid).build(), new JWTClaimsSet.Builder().audience("Test").build());
        jwt.sign(new ECDSASigner(testKey));
        assertThat(jwt.verify(new ECDSAVerifier((ECKey) loadedKey))).as("Signature MUST be valid").isTrue();
    }
}