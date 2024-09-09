package ch.admin.bit.eid.oid4vp.model.did;

import ch.admin.eid.didresolver.Did;
import ch.admin.eid.didresolver.DidResolveException;
import ch.admin.eid.didtoolbox.DidDoc;
import org.springframework.stereotype.Service;

/**
 * Adapter for loading a DID Documents by a DID (Decentralized Identifier).
 */
@Service
public class DidResolverAdapter {
    /**
     * Returns the DID Document for the given DID.
     *
     * @param did - the id of the DID Document
     * @return the DID Document for the given DID
     */
    public DidDoc resolveDid(String did) throws DidResolveException {
        try (var d = new Did(did)) {
            return d.resolve();
        }
    }
}
