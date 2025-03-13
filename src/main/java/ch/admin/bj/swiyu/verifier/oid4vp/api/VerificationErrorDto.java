/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.api;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

/**
 * RFC 6749 Error codes as mentioned in <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#section-6.4">OpenID4VP</a>
 * OpenID for Verifiable Presentations</a> which are supported by this implementation.
 */
@AllArgsConstructor
@Schema(name = "VerificationError", enumAsRef = true, description = """
        | Value              | Description                                                                                  |
        |--------------------|----------------------------------------------------------------------------------------------|
        | invalid_request    | The request was invalid.<br>This is a general purpose code if none of the other codes apply. |
        | server_error       | The authorization server encountered an unexpected                                           |
        | invalid_credential | The credential presented during validation was deemed invalid.                               |
        | client_rejected    | The End-User did not give consent to share the requested Credentials with the Verifier.      |
        """)
public enum VerificationErrorDto {
    // RFC codes
    INVALID_REQUEST("invalid_request"),
    SERVER_ERROR("server_error"),

    // Codes according to custom profile
    INVALID_CREDENTIAL("invalid_credential");

    private final String displayName;

    @JsonValue
    @Override
    public String toString() {
        return this.displayName;
    }
}
