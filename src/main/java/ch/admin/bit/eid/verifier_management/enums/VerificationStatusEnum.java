package ch.admin.bit.eid.verifier_management.enums;

public enum VerificationStatusEnum {
    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private String displayName;

    VerificationStatusEnum(String displayName) {
        this.displayName = displayName;
    }
}
