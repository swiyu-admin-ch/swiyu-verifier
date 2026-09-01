package ch.admin.bj.swiyu.verifier.dto.management.result;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TrustMethod", enumAsRef = true)
public enum TrustMethodDto {
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
