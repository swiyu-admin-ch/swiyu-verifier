package ch.admin.bit.eid.oid4vp.model.verification;

public enum VerificationStatus {
    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private String displayName;

    VerificationStatus(String displayName) {
        this.displayName = displayName;
    }
}