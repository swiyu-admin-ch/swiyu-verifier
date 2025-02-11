/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.api;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Custom Error Codes expanding on OID4VP defined errors to give additional information on what is wrong
 */
@Getter
@AllArgsConstructor
@Schema(name = "VerificationErrorResponseCode", enumAsRef = true)
public enum VerificationErrorResponseCodeDto {
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
    CLIENT_REJECTED("client_rejected"),
    ISSUER_NOT_ACCEPTED("issuer_not_accepted");

    private final String displayName;

    @JsonValue
    @Override
    public String toString() {
        return this.displayName;
    }
}
