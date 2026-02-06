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

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.DID_DOC_CACHE;
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
     * Returns the JWK (verification method) for the given DID and key fragment.
     *
     * @param didId    the id of the DID Document
     * @param fragment the fragment part of the key id (after '#')
     * @return the JWK for the given DID and fragment
     */
    @Cacheable(DID_DOC_CACHE)
    public Jwk resolveDid(String didId, String fragment) throws DidResolverException, DidSidekicksException {
        if (didId == null) {
            throw new IllegalArgumentException("did must not be null");
        }
        DidDoc didDoc = didResolverAdapter.resolveDid(didId, urlRewriteProperties.getUrlMappings());
        return didDoc.getKey(fragment);
    }

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