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
@Schema(name = "VerificationErrorResponseCode", enumAsRef = true, description = """       
| Value                                       | Description                                                                                                                          |
|---------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| credential_invalid                          | The credential presented during validation was deemed invalid.<br>This is a general purpose code if none of the other codes apply.   |
| jwt_expired                                 | During the verification process an expired jwt was used.                                                                             |
| missing_nonce                               | During the verification process a nonce was missing.                                                                                 |
| invalid_format                              | The data send in the verification process used an invalid format.                                                                    |
| credential_expired                          | The credential presented during validation was expired.                                                                              |
| unsupported_format                          | The credential presented during validation was in an unsupported format.                                                             |
| credential_revoked                          | The credential presented during validation was revoked.                                                                              |
| credential_suspended                        | The credential presented during validation was suspended.                                                                            |
| credential_missing_data                     | The credential presented during validation does not contain the required fields.                                                     |
| unresolvable_status_list                    | The credential presented during validation contains a status list which cannot be reached during validation.                         |
| public_key_of_issuer_unresolvable           | The credential presented during validation was issued by an entity that does not provide the public key at the time of verification. |
| issuer_not_accepted                         | The credential presented during validation was issued by an entity that is not in the list of allowed issuers.                       |
| malformed_credential                        | The credential presented during validation isnt valid according to the format specification in question                              |
| holder_binding_mismatch                     | The holder has provided invalid proof that the credential is under their control.                                                    |
| client_rejected                             | The holder rejected the verification request.                                                                                        |
| issuer_not_accepted                         | The issuer of the vc was not in the allow-list given in the verificaiton request.                                                    |
| authorization_request_missing_error_param   | During the verification process a required parameter (eg.: vp_token, presentation) was not provided in the request.                  |
| authorization_request_object_not_found      | The requested verification process cannot be found.                                                                                  |
| verification_process_closed                 | The requested verification process is already closed.                                                                                |
| invalid_presentation_definition             | The provided credential presentation was invalid.                                                                                    |
| presentation_submission_constraint_violated | The presentation submission provided violated at least one constraint defined in the presentation definition                         |
| invalid_presentation_submission             | The presentation submission couldn't be deserialized and is therefore invalid                                                        |
        """)
public enum VerificationErrorResponseCodeDto {

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
    ISSUER_NOT_ACCEPTED("issuer_not_accepted"),
    AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND("authorization_request_object_not_found"),
    AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM("authorization_request_missing_error_param"),
    VERIFICATION_PROCESS_CLOSED("verification_process_closed"),
    INVALID_PRESENTATION_DEFINITION("invalid_presentation_definition"),
    MALFORMED_CREDENTIAL("malformed_credential"),
    PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED("presentation_submission_constraint_violated"),
    INVALID_PRESENTATION_SUBMISSION("invalid_presentation_submission");

    private final String displayName;

    @JsonValue
    @Override
    public String toString() {
        return this.displayName;
    }
}
