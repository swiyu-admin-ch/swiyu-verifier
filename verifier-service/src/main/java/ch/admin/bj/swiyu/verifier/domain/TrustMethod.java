package ch.admin.bj.swiyu.verifier.domain;

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
