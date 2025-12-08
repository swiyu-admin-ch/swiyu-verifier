package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.service.dcql.DcqlUtil;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.DcqlEvaluator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter: implementiert DcqlEvaluator und delegiert an DcqlUtil.
 */
@Component
public class DcqlEvaluatorAdapter implements DcqlEvaluator {
    @Override
    public List<SdJwt> filterByVct(List<SdJwt> sdJwts, DcqlCredentialMeta meta) {
        return DcqlUtil.filterByVct(sdJwts, meta);
    }

    @Override
    public void validateRequestedClaims(SdJwt sdJwt, List<DcqlClaim> requestedClaims) {
        DcqlUtil.validateRequestedClaims(sdJwt, requestedClaims);
    }
}

