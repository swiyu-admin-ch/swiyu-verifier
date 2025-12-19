package ch.admin.bj.swiyu.verifier.service.oid4vp.ports;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;

import java.util.List;

/**
 * Port: evaluates DCQL against verified SdJwt claims.
 */
public interface DcqlEvaluator {
    List<SdJwt> filterByVct(List<SdJwt> sdJwts, DcqlCredentialMeta meta);
    void validateRequestedClaims(SdJwt sdJwt, List<DcqlClaim> requestedClaims);
}
