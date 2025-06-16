/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import ch.admin.eid.didresolver.Did;
import ch.admin.eid.didtoolbox.DidDoc;
import ch.admin.eid.didtoolbox.TrustDidWeb;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.ISSUER_PUBLIC_KEY_CACHE;


/**
 * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
 */
@Service
@AllArgsConstructor
public class DidResolverAdapter {

    private final UrlRewriteProperties urlRewriteProperties;
    private final RestClient restClient;

    /**
     * Returns the DID Document for the given DID.
     *
     * @param didTdw - the id of the DID Document
     * @return the DID Document for the given DID
     */
    @Cacheable(value = ISSUER_PUBLIC_KEY_CACHE, key = "#didTdw")
    public DidDoc resolveDid(String didTdw) throws DidResolverException {
        if (didTdw == null) {
            throw new IllegalArgumentException("didTdw must not be null");
        }
        try (var did = new Did(didTdw)) {
            String didUrl = did.getUrl();
            String didLog = retrieveDidLog(didUrl);
            try (TrustDidWeb tdw = TrustDidWeb.Companion.read(didTdw, didLog)) {
                String rawDidDoc = tdw.getDidDoc();
                return DidDoc.Companion.fromJson(rawDidDoc);
            }
        } catch (Exception e) {
            throw new DidResolverException(e);
        }
    }

    private String retrieveDidLog(String uri) {
        return restClient.get().uri(urlRewriteProperties.getRewrittenUrl(uri)).retrieve().body(String.class);
    }
}