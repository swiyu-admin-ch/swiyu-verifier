package ch.admin.bj.swiyu.verifier.domain;

// JSON-PERSISTED (ZDD): referenced from IssuerTrustMarker, which is serialized to JSON in the "management" table.
public enum TrustMethod {
    TRUST_PROTOCOL_1_0,
    /**
     * Trust was established using Trust Protocol 2.0 through a trusted trust issuer
     */
    TRUST_PROTOCOL_2_0,
    /**
     * Trust was established by inherently trusting a certain did
     */
    TRUSTED_AUTHORITY;

}
