package ch.admin.bit.eid.oid4vp.model.did;

import ch.admin.bit.eid.oid4vp.config.ApplicationProperties;
import ch.admin.bit.eid.oid4vp.config.UrlRewriteConfig;
import ch.admin.eid.didresolver.Did;
import ch.admin.eid.didresolver.DidResolveException;
import ch.admin.eid.didtoolbox.DidDoc;
import ch.admin.eid.didtoolbox.TrustDidWeb;
import ch.admin.eid.didtoolbox.TrustDidWebException;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
 */
@Service
@AllArgsConstructor
public class DidResolverAdapter {
    /**
     * Returns the DID Document for the given DID.
     *
     * @param did - the id of the DID Document
     * @return the DID Document for the given DID
     */
    private final UrlRewriteConfig urlRewriteConfig;

    public DidDoc resolveDid(String didTdw) throws DidResolveException, TrustDidWebException {
        try (var did = new Did(didTdw)) {
            String didUrl = did.getUrl();
            String didLog = retrieveDidLog(didUrl);
            TrustDidWeb tdw = TrustDidWeb.Companion.read(didTdw, didLog, true);
            String rawDidDoc = tdw.getDidDoc();
            return DidDoc.Companion.fromJson(rawDidDoc);
        }
    }

    private String retrieveDidLog(String uri) {
        return RestClient.create().get().uri(urlRewriteConfig.getRewrittenUrl(uri)).retrieve().body(String.class);
    }
}
