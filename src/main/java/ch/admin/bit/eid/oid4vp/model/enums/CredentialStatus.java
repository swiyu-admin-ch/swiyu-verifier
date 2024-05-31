package ch.admin.bit.eid.oid4vp.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CredentialStatus {
        OFFERED("Offered"),
        CANCELLED("Cancelled"),
        IN_PROGRESS("Claiming in Progress"),
        ISSUED("Issued"),
        SUSPENDED("Suspended"),
        REVOKED("Revoked"),
        EXPIRED("Expired");

        private final String displayName;

    @Override
    public String toString() {
        return this.getDisplayName();
    }
}
