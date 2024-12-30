package ch.admin.bj.swiyu.verifier.management.domain.management;

/**
 * The wallet response data, only persisted during oid4vp, will be used by verifier-agent-management.
 */
public record ResponseData(
        VerificationErrorResponseCode errorCode,
        String errorDescription,
        String credentialSubjectData
) {
}
