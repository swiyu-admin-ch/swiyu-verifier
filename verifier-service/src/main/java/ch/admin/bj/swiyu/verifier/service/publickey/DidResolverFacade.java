/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.bj.swiyu.didresolveradapter.DidResolverAdapter;
import ch.admin.bj.swiyu.didresolveradapter.DidResolverException;
import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.eid.did_sidekicks.DidDoc;
import ch.admin.eid.did_sidekicks.DidSidekicksException;
import ch.admin.eid.did_sidekicks.Jwk;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.JWK_CACHE;
import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.TRUST_STATEMENT_CACHE;


/**
 * Adapter for loading information derived from DID Documents by a DID (Decentralized Identifier).
 */
@Service
@AllArgsConstructor
@Slf4j
public class DidResolverFacade {

    private final DidResolverAdapter didResolverAdapter;
    private final UrlRewriteProperties urlRewriteProperties;

    /**
     * Resolves the DID Document for the given DID and returns the JWK (verification method) for the specified fragment.
     * <p>
     * The DID Document is handled via try-with-resources and closed automatically. Only the JWK for the fragment is returned.
     * </p>
     *
     * @param didId    the id of the DID Document (e.g. "did:example:123")
     * @param fragment the fragment part of the key id (e.g. "key-1" for "did:example:123#key-1")
     * @return the JWK for the given DID and fragment
     * @throws DidResolverException if the DID resolution fails
     * @throws DidSidekicksException if the DID document or key extraction fails
     * @throws IllegalArgumentException if didId is null
     */
    @Cacheable(JWK_CACHE)
    public Jwk resolveDid(String didId, String fragment)
            throws DidResolverException, DidSidekicksException {
        if (didId == null) {
            throw new IllegalArgumentException("did must not be null");
        }
        try (DidDoc didDoc = didResolverAdapter.resolveDid(didId, urlRewriteProperties.getUrlMappings())) {
            return didDoc.getKey(fragment);
        }
    }

    /**
     * Resolves the trust statement for the given trust registry URL and VCT.
     * <p>
     * The result is cached. If the resolution fails (e.g. HTTP error), null is returned and a log entry is written.
     * </p>
     *
     * @param trustRegistryUrl the URL of the trust registry
     * @param vct the VCT identifier
     * @return the trust statement as a String, or null if resolution fails
     */
    @Cacheable(TRUST_STATEMENT_CACHE)
    public String resolveTrustStatement(String trustRegistryUrl, String vct) {
        try {
            return didResolverAdapter.resolveTrustStatement(trustRegistryUrl, vct, urlRewriteProperties.getUrlMappings());
        } catch (HttpStatusCodeException e) {
            log.info("Failed retrieving trust statement for {} {}", trustRegistryUrl, vct);
            return null;
        }
    }

}