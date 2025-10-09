/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.eid.didresolver.Did;
import ch.admin.eid.didtoolbox.DidDoc;
import ch.admin.eid.didtoolbox.TrustDidWeb;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;


/**
 * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
 */
@Service
@AllArgsConstructor
@Slf4j
public class DidResolverAdapter {

    private final DidResolverRestClient didResolverRestClient;

    /**
     * Returns the DID Document for the given DID.
     *
     * @param didTdw - the id of the DID Document
     * @return the DID Document for the given DID
     */
    public DidDoc resolveDid(String didTdw) throws DidResolverException {
        if (didTdw == null) {
            throw new IllegalArgumentException("didTdw must not be null");
        }
        try (var did = new Did(didTdw)) {
            String didUrl = did.getUrl();
            String didLog = didResolverRestClient.retrieveDidLog(didUrl);
            try (DidDoc didDoc = did.resolve(didLog)) {
                return didDoc;
            }
        } catch (Exception e) {
            throw new DidResolverException(e);
        }
    }

    public String resolveTrustStatement(String trustRegistryUrl, String vct) {
        try {
            return didResolverRestClient.retrieveDidTrustStatement(trustRegistryUrl, vct);
        } catch (HttpStatusCodeException e) {
            log.info("Failed retrieving trust statement for {} {}", trustRegistryUrl, vct);
            return null;
        }
    }

}