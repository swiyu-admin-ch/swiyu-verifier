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
@Schema(name = "VerificationError", enumAsRef = true)
public enum VerificationErrorDto {
    AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND("authorization_request_object_not_found"),
    AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM("authorization_request_missing_error_param"),
    VERIFICATION_PROCESS_CLOSED("verification_process_closed"),
    INVALID_PRESENTATION_DEFINITION("invalid_presentation_definition"),
    INVALID_REQUEST("invalid_request");

    private final String displayName;

    @JsonValue
    @Override
    public String toString() {
        return this.displayName;
    }
}
