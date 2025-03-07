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
 * RFC 6749 Error codes as metioned in <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-7.5">
 * OpenID for Verifiable Presentations</a>.
 */
@AllArgsConstructor
@Schema(name = "VerificationError", enumAsRef = true, description = """
        
        | Value                                     | Description                                                                                                          |
        | ----------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
        | invalid_request                           | The request was invalid.<br>This is a general purpose code if none of the other codes apply.                         |
        | authorization_request_missing_error_param | During the verification process a required parameter (eg.: vp_token, presentation) was not provided in the request. |
        | authorization_request_object_not_found    | The requested verification process cannot be found.                                                                  |
        | verification_process_closed               | The requested verification process is already closed.                                                                |
        | invalid_presentation_definition           | The provided credential presentation was invalid.                                                                    |
        """)
public enum VerificationErrorDto {
    INVALID_REQUEST("invalid_request"),
    SERVER_ERROR("server_error"),
    INVALID_CREDENTIAL("invalid_credential");

    private final String displayName;

    @JsonValue
    @Override
    public String toString() {
        return this.displayName;
    }
}
