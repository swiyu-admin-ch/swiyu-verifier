package ch.admin.bj.swiyu.verifier.domain.management;

import java.util.Set;

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

    private static final Set<VerificationStatus> TERMINAL_STATES = Set.of(SUCCESS, FAILED);

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }
}
