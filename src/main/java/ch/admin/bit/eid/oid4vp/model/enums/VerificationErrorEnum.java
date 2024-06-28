package ch.admin.bit.eid.oid4vp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * RFC 6749 Error codes
 */
@Getter
@AllArgsConstructor
public enum VerificationErrorEnum {
    AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND("authorization_request_object_not_found"),
    AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM("authorization_request_missing_error_param"),
    VERIFICATION_PROCESS_CLOSED("verification_process_closed"),
    INVALID_PRESENTATION_DEFINITION("invalid_presentation_definition"),
    INVALID_REQUEST("invalid_request");

    private final String displayName;

    @JsonValue
    @Override
    public String toString() {
        return this.getDisplayName();
    }
}
