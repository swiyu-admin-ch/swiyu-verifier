/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp.domain.publickey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverException;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.publickey.DidResolverWebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;


class DidResolverAdapterTest {
    private final WebClient webClient;

    private final DidResolverAdapter didResolverAdapter;

    DidResolverAdapterTest() throws JsonProcessingException {
        webClient = mock(WebClient.class, Mockito.RETURNS_DEEP_STUBS);
        var urlRewriteProperties = new UrlRewriteProperties();
        urlRewriteProperties.setMapping("""
                {"https://identifier-reg.trust-infra.swiyu-int.admin.ch":"https://test.replacement"}
                """);
        urlRewriteProperties.init();

        DidResolverWebClient didResolverWebClient = new DidResolverWebClient(urlRewriteProperties, webClient);
        didResolverAdapter = new DidResolverAdapter(didResolverWebClient);
    }

    @Test
    void validDidResolving() {
        // GIVEN
        var did = "did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212";
        Mockito.when(webClient.get().uri(Mockito.anyString()).retrieve().bodyToMono(String.class).block()).thenReturn("""
                ["1-QmdJiFHQ3gHMyRUnW6Rri6hHwKRrQUJadBRoirLZUJtsmC","2025-01-31T09:35:11Z",{"method":"did:tdw:0.3","scid":"QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ","updateKeys":["z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv"]},{"value":{"@context":["https://www.w3.org/ns/did/v1","https://w3id.org/security/suites/jws-2020/v1"],"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","authentication":["did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#auth-key-01"],"assertionMethod":["did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#assert-key-01"],"verificationMethod":[{"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#auth-key-01","controller":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","type":"JsonWebKey2020","publicKeyJwk":{"kty":"EC","crv":"P-256","x":"D3nYTvdvNL0wRvm4bu92CjntEpDfI8bfQdQhaaD6Qv8","y":"oLe56pmgQWmhAo5eviw2XFNHjmGhepy9RzQSseUXGIU","kid":"auth-key-01"}},{"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#assert-key-01","controller":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","type":"JsonWebKey2020","publicKeyJwk":{"kty":"EC","crv":"P-256","x":"1fwnwoN8zatr6kD_bvwY2zQDV4D6blE7mzTliQF11Jc","y":"9-cDZlPqXVlJnE0rcUUyy7P_15x7RLE-jiNGqHA9FP4","kid":"assert-key-01"}}]}},[{"type":"DataIntegrityProof","cryptosuite":"eddsa-jcs-2022","created":"2025-01-31T09:35:11Z","verificationMethod":"did:key:z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv#z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv","proofPurpose":"authentication","challenge":"1-QmdJiFHQ3gHMyRUnW6Rri6hHwKRrQUJadBRoirLZUJtsmC","proofValue":"z2HuP8d1Wk6mLZpp2QmxywGNSDAi2CxfgoE7FJoeB1DSfUfg2kUyokAaea1Bqz5Q6L5FaukkD1KdxUpU45z1TUB3R"}]]
                """);
        // WHEN
        try (var didDoc = didResolverAdapter.resolveDid(did)) {
            // THEN
            assertThat(didDoc.getId()).isEqualTo(did);
        }
    }

    @Test
    void validDidResolvingWithMapping() {
        // GIVEN
        var did = "did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212";
        Mockito.when(webClient.get().uri("https://test.replacement/api/v1/did/2e246676-209a-4c21-aceb-721f8a90b212/did.jsonl").retrieve().bodyToMono(String.class).block()).thenReturn("""
                ["1-QmdJiFHQ3gHMyRUnW6Rri6hHwKRrQUJadBRoirLZUJtsmC","2025-01-31T09:35:11Z",{"method":"did:tdw:0.3","scid":"QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ","updateKeys":["z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv"]},{"value":{"@context":["https://www.w3.org/ns/did/v1","https://w3id.org/security/suites/jws-2020/v1"],"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","authentication":["did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#auth-key-01"],"assertionMethod":["did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#assert-key-01"],"verificationMethod":[{"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#auth-key-01","controller":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","type":"JsonWebKey2020","publicKeyJwk":{"kty":"EC","crv":"P-256","x":"D3nYTvdvNL0wRvm4bu92CjntEpDfI8bfQdQhaaD6Qv8","y":"oLe56pmgQWmhAo5eviw2XFNHjmGhepy9RzQSseUXGIU","kid":"auth-key-01"}},{"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#assert-key-01","controller":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","type":"JsonWebKey2020","publicKeyJwk":{"kty":"EC","crv":"P-256","x":"1fwnwoN8zatr6kD_bvwY2zQDV4D6blE7mzTliQF11Jc","y":"9-cDZlPqXVlJnE0rcUUyy7P_15x7RLE-jiNGqHA9FP4","kid":"assert-key-01"}}]}},[{"type":"DataIntegrityProof","cryptosuite":"eddsa-jcs-2022","created":"2025-01-31T09:35:11Z","verificationMethod":"did:key:z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv#z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv","proofPurpose":"authentication","challenge":"1-QmdJiFHQ3gHMyRUnW6Rri6hHwKRrQUJadBRoirLZUJtsmC","proofValue":"z2HuP8d1Wk6mLZpp2QmxywGNSDAi2CxfgoE7FJoeB1DSfUfg2kUyokAaea1Bqz5Q6L5FaukkD1KdxUpU45z1TUB3R"}]]
                """);
        // WHEN
        try (var didDoc = didResolverAdapter.resolveDid(did)) {
            // THEN
            assertThat(didDoc.getId()).isEqualTo(did);
        }
    }

    @Test
    void testInvalidScidMismatchDidResolving() {
        // GIVEN
        // did contains invalid scid
        var did = "did:tdw:QmWrXWFEDenvoYWFXxSQTFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212";
        Mockito.when(webClient.get().uri(Mockito.anyString()).retrieve().bodyToMono(String.class).block()).thenReturn("""
                ["1-QmdJiFHQ3gHMyRUnW6Rri6hHwKRrQUJadBRoirLZUJtsmC","2025-01-31T09:35:11Z",{"method":"did:tdw:0.3","scid":"QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ","updateKeys":["z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv"]},{"value":{"@context":["https://www.w3.org/ns/did/v1","https://w3id.org/security/suites/jws-2020/v1"],"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","authentication":["did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#auth-key-01"],"assertionMethod":["did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#assert-key-01"],"verificationMethod":[{"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#auth-key-01","controller":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","type":"JsonWebKey2020","publicKeyJwk":{"kty":"EC","crv":"P-256","x":"D3nYTvdvNL0wRvm4bu92CjntEpDfI8bfQdQhaaD6Qv8","y":"oLe56pmgQWmhAo5eviw2XFNHjmGhepy9RzQSseUXGIU","kid":"auth-key-01"}},{"id":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212#assert-key-01","controller":"did:tdw:QmWrXWFEDenvoYWFXxSQGFCa6Pi22Cdsg2r6weGhY2ChiQ:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:2e246676-209a-4c21-aceb-721f8a90b212","type":"JsonWebKey2020","publicKeyJwk":{"kty":"EC","crv":"P-256","x":"1fwnwoN8zatr6kD_bvwY2zQDV4D6blE7mzTliQF11Jc","y":"9-cDZlPqXVlJnE0rcUUyy7P_15x7RLE-jiNGqHA9FP4","kid":"assert-key-01"}}]}},[{"type":"DataIntegrityProof","cryptosuite":"eddsa-jcs-2022","created":"2025-01-31T09:35:11Z","verificationMethod":"did:key:z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv#z6MkrU7wPQwBXsnYzWVJMWbvq61ZeDib6v9aQ3DpXu7qWagv","proofPurpose":"authentication","challenge":"1-QmdJiFHQ3gHMyRUnW6Rri6hHwKRrQUJadBRoirLZUJtsmC","proofValue":"z2HuP8d1Wk6mLZpp2QmxywGNSDAi2CxfgoE7FJoeB1DSfUfg2kUyokAaea1Bqz5Q6L5FaukkD1KdxUpU45z1TUB3R"}]]
                """);

        assertThatThrownBy(
                // WHEN
                () -> didResolverAdapter.resolveDid(did)
        )
                // THAT
                .isInstanceOf(DidResolverException.class);
    }
}