package ch.admin.bj.swiyu.verifier.management.domain.management;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Custom Error Codes expanding on OID4VP defined errors to give additional information on what is wrong
 */
@Getter
@AllArgsConstructor
public enum VerificationErrorResponseCode {
    CREDENTIAL_INVALID("credential_invalid"),
    JWT_EXPIRED("jwt_expired"),
    INVALID_FORMAT("invalid_format"),
    CREDENTIAL_EXPIRED("credential_expired"),
    MISSING_NONCE("missing_nonce"),
    UNSUPPORTED_FORMAT("unsupported_format"),
    CREDENTIAL_REVOKED("credential_revoked"),
    CREDENTIAL_SUSPENDED("credential_suspended"),
    HOLDER_BINDING_MISMATCH("holder_binding_mismatch"),
    CREDENTIAL_MISSING_DATA("credential_missing_data"),
    UNRESOLVABLE_STATUS_LIST("unresolvable_status_list"),
    PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE("public_key_of_issuer_unresolvable"),
    CLIENT_REJECTED("client_rejected");

    // the value as it is written to json -> only needed because we already have data like this
    // stored in the db (would be a breaking change to simplify it).
    private final String jsonValue;

    @JsonValue
    @Override
    public String toString() {
        return this.jsonValue;
    }
}