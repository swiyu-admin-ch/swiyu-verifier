package ch.admin.bj.swiyu.verifier.domain.management;

public enum VerificationStatus {
    PENDING,
    /**
     * The presentation response has been received and is currently being processed.
     * This status acts as an exclusive claim to prevent concurrent duplicate submissions
     * (TOCTOU / race condition protection).
     */
    IN_PROGRESS,
    SUCCESS,
    FAILED;
}
