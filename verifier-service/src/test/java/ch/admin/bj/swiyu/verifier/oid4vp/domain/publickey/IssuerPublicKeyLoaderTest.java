/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey;

import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.DidDocFixtures;
import ch.admin.bj.swiyu.verifier.oid4vp.test.fixtures.KeyFixtures;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.BeforeEach;
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
    void loadPublicKey_MultibaseKey() throws LoadingPublicKeyOfIssuerFailedException, JOSEException, DidSidekicksException {
        // GIVEN (an issuer registered in the DID registry and an issuer signed SD-JWT)
        var issuerDidDocument = DidDocFixtures.issuerDidDocWithMultikey(
                "did:example:123",
                "did:example:123#key-1",
                KeyFixtures.issuerPublicKeyAsMultibaseKey());
        var issuerDidTdw = issuerDidDocument.getId();
        var issuerKeyId = issuerDidDocument.getVerificationMethod().getFirst().getId();
        when(mockedDidResolverAdapter.resolveDid(issuerDidTdw)).thenReturn(issuerDidDocument);

        // WHEN
        var publicKey = publicKeyLoader.loadPublicKey(issuerDidTdw, issuerKeyId);

        // THEN
        assertThat(publicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(publicKey.getEncoded()).isEqualTo(KeyFixtures.issuerPublicKeyEncoded());
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
        when(mockedDidResolverAdapter.resolveDid(issuerDidId)).thenReturn(issuerDidDocument);

        // WHEN
        var publicKey = publicKeyLoader.loadPublicKey(issuerDidId, issuerKeyId);

        // THEN
        assertThat(publicKey.getAlgorithm()).isEqualTo("EC");
        assertThat(publicKey.getFormat()).isEqualTo("X.509");
        assertThat(publicKey.getEncoded()).isEqualTo(KeyFixtures.issuerPublicKeyEncoded());
    }
}