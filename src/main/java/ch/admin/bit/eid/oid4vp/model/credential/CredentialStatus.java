package ch.admin.bit.eid.oid4vp.model.credential;

public enum CredentialStatus {
        OFFERED("Offered"),
        CANCELLED("Cancelled"),
        IN_PROGRESS("Claiming in Progress"),
        ISSUED("Issued"),
        SUSPENDED("Suspended"),
        REVOKED("Revoked"),
        EXPIRED("Expired");

        private String displayName;

        CredentialStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

    @Override
    public String toString() {
        return this.getDisplayName();
    }
}
