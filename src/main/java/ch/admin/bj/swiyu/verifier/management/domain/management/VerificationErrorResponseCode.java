/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

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
    ISSUER_NOT_ACCEPTED("issuer_not_accepted"),
    AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND("authorization_request_object_not_found"),
    AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM("authorization_request_missing_error_param"),
    VERIFICATION_PROCESS_CLOSED("verification_process_closed"),
    INVALID_PRESENTATION_DEFINITION("invalid_presentation_definition"),
    MALFORMED_CREDENTIAL("malformed_credential"),
    PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED("presentation_submission_constraint_violated"),
    INVALID_PRESENTATION_SUBMISSION("invalid_presentation_submission"),
    INVALID_SCOPE("invalid_scope"),
    INVALID_REQUEST("invalid_request"),
    INVALID_CLIENT("invalid_client"),
    VP_FORMATS_NOT_SUPPORTED("vp_formats_not_supported"),
    INVALID_PRESENTATION_DEFINITION_URI("invalid_presentation_definition_uri"),
    INVALID_PRESENTATION_DEFINITION_REFERENCE("invalid_presentation_definition_reference"),
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