package ch.admin.bj.swiyu.verifier.management.domain.management;

import lombok.Builder;

/**
 * The wallet response data, only persisted during oid4vp, will be used by verifier-agent-management.
 */
@Builder
public record ResponseData(
        VerificationErrorResponseCode errorCode,
        String errorDescription,
        String credentialSubjectData
) {
}
