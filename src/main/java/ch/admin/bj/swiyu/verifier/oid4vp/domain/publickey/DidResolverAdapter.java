/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey;

import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.DidResolverException;
import ch.admin.eid.didresolver.Did;
import ch.admin.eid.didtoolbox.DidDoc;
import ch.admin.eid.didtoolbox.TrustDidWeb;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
 */
@Service
@AllArgsConstructor
public class DidResolverAdapter {

    private final UrlRewriteProperties urlRewriteProperties;
    private final RestClient.Builder restClientBuilder;

    /**
     * Returns the DID Document for the given DID.
     *
     * @param didTdw - the id of the DID Document
     * @return the DID Document for the given DID
     */
    public DidDoc resolveDid(String didTdw) throws DidResolverException {
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
        return restClientBuilder.build().get().uri(urlRewriteProperties.getRewrittenUrl(uri)).retrieve().body(String.class);
    }
}
