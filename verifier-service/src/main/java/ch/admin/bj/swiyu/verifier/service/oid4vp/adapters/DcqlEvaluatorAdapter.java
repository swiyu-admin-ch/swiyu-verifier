package ch.admin.bj.swiyu.verifier.service.oid4vp.adapters;

import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlClaim;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredentialMeta;
import ch.admin.bj.swiyu.verifier.service.dcql.DcqlUtil;
import ch.admin.bj.swiyu.verifier.service.oid4vp.ports.DcqlEvaluator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter implementation of {@link DcqlEvaluator}.
 * <p>
 * This class acts as a thin wrapper around the static utility methods provided by {@link DcqlUtil}.
 * It allows Spring to inject an implementation of {@link DcqlEvaluator} wherever it is required,
 * without the calling code having to know about or depend directly on the utility class.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Expose DcqlUtil-based filtering logic as a Spring-managed bean.</li>
 *   <li>Delegate credential filtering by VCT (Verifiable Credential Type) to {@link DcqlUtil#filterByVct(List, DcqlCredentialMeta)}.</li>
 *   <li>Delegate validation of requested claims to {@link DcqlUtil#validateRequestedClaims(SdJwt, List)}.</li>
 * </ul>
 */
@Component
public class DcqlEvaluatorAdapter implements DcqlEvaluator {

    /**
     * Filters the given list of SD-JWTs so that only credentials matching the
     * VCT (Verifiable Credential Type) requirements defined in {@code meta} are returned.
     * <p>
     * This method delegates all logic to {@link DcqlUtil#filterByVct(List, DcqlCredentialMeta)}.
     *
     * @param sdJwts the list of SD-JWT credentials to be filtered
     * @param meta   the DCQL credential metadata describing which VCTs are acceptable
     * @return a list containing only those SD-JWTs that satisfy the VCT constraints
     */
    @Override
    public List<SdJwt> filterByVct(List<SdJwt> sdJwts, DcqlCredentialMeta meta) {
        return DcqlUtil.filterByVct(sdJwts, meta);
    }

    /**
     * Validates that the provided {@link SdJwt} satisfies the given DCQL requested claims.
     * <p>
     * All validation logic is delegated to {@link DcqlUtil#validateRequestedClaims(SdJwt, List)}.
     * If the SD-JWT does not fulfil the requested claims, that method is expected to throw
     * an appropriate exception.
     *
     * @param sdJwt           the SD-JWT to be validated
     * @param requestedClaims the DCQL claim definitions that must be satisfied by the SD-JWT
     */
    @Override
    public void validateRequestedClaims(SdJwt sdJwt, List<DcqlClaim> requestedClaims) {
        DcqlUtil.validateRequestedClaims(sdJwt, requestedClaims);
    }
}
