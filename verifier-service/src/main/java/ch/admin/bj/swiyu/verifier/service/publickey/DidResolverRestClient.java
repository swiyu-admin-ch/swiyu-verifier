/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.publickey;

import ch.admin.bj.swiyu.verifier.common.config.UrlRewriteProperties;
import lombok.AllArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.ISSUER_PUBLIC_KEY_CACHE;
import static ch.admin.bj.swiyu.verifier.common.config.CachingConfig.TRUST_STATEMENT_CACHE;


/**
 * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
 */
@Service
@AllArgsConstructor
public class DidResolverRestClient {

    private final UrlRewriteProperties urlRewriteProperties;
    private final RestClient restClient;


    /**
     * Returns the DID Document as string for the given didUrl.
     * This method is cached to avoid multiple calls to the DID resolver service for the same DID URL.
     *
     * @return the DID Document as string for the given didUrl
     */
    @Cacheable(ISSUER_PUBLIC_KEY_CACHE)
    public String retrieveDidLog(String didUrl) {
        return restClient.get().uri(urlRewriteProperties.getRewrittenUrl(didUrl)).retrieve().body(String.class);
    }

    /**
     * Returns the Trust Statement VC for the given did from the trust registry
     */
    @Cacheable(TRUST_STATEMENT_CACHE)
    public String retrieveDidTrustStatement(String trustRegistryUrl, String did) throws HttpClientErrorException, HttpServerErrorException {
        return restClient.get().uri(urlRewriteProperties.getRewrittenUrl(trustRegistryUrl + "/" + did)).retrieve().body(String.class);
    }
}