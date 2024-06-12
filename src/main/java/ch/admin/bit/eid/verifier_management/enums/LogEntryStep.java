package ch.admin.bit.eid.verifier_management.enums;

public enum LogEntryStep {
    ISSUANCE_PREPARATION("PREPARATION"),
    ISSUANCE_DELIVERY("DELIVERY"),
    ISSUANCE_EXPIRY("EXPIRY"),

    VERIFICATION_REQUEST("REQUEST"),
    VERIFICATION_EVALUATION("EVALUATION"),
    VERIFICATION_RESPONSE("RESPONSE");


    LogEntryStep(String displayName) {
    }
}
