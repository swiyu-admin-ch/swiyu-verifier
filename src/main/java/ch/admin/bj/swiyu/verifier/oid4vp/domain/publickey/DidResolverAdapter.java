package ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey;

import ch.admin.bj.swiyu.verifier.oid4vp.common.config.UrlRewriteProperties;
import ch.admin.eid.didresolver.Did;
import ch.admin.eid.didresolver.DidResolveException;
import ch.admin.eid.didtoolbox.DidDoc;
import ch.admin.eid.didtoolbox.TrustDidWeb;
import ch.admin.eid.didtoolbox.TrustDidWebException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
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
    public DidDoc resolveDid(String didTdw) throws DidResolveException, TrustDidWebException {
        try (var did = new Did(didTdw)) {
            String didUrl = did.getUrl();
            String didLog = retrieveDidLog(didUrl);
            TrustDidWeb tdw = TrustDidWeb.Companion.read(didTdw, didLog, true);
            String rawDidDoc = tdw.getDidDoc();
            return DidDoc.Companion.fromJson(rawDidDoc);
        } catch (HttpServerErrorException | DidResolveException | TrustDidWebException e) {
            throw e;

        // there are cases where we receive unchecked general exceptions from the rust library
        // in order to tackle this, in such a case we assume it is a user error (something with the did is wrong)
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private String retrieveDidLog(String uri) {
        return restClientBuilder.build().get().uri(urlRewriteProperties.getRewrittenUrl(uri)).retrieve().body(String.class);
    }
}
