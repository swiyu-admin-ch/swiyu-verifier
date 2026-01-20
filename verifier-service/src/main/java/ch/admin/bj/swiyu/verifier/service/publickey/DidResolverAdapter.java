/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.eid.didresolver.Did;
import ch.admin.eid.did_sidekicks.DidDoc;
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

    private final DidResolverWebClient didResolverWebClient;

    /**
     * Returns the DID Document for the given DID.
     *
     * @param didId - the id of the DID Document
     * @return the DID Document for the given DID
     */
    public DidDoc resolveDid(String didId) throws DidResolverException {
        if (didId == null) {
            throw new IllegalArgumentException("did must not be null");
        }
        try (var did = new Did(didId)) {
            String didUrl = did.getUrl();
            String didLog = didResolverWebClient.retrieveDidLog(didUrl);
            return did.resolve(didLog);
        } catch (Exception e) {
            throw new DidResolverException(e);
        }
    }

    public String resolveTrustStatement(String trustRegistryUrl, String vct) {
        try {
            return didResolverWebClient.retrieveDidTrustStatement(trustRegistryUrl, vct);
        } catch (HttpStatusCodeException e) {
            log.info("Failed retrieving trust statement for {} {}", trustRegistryUrl, vct);
            return null;
        }
    }

}