package ch.admin.bj.swiyu.verifier.oid4vp.api;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

/**
 * Allowed error responses the wallet can make according to https://openid.net/specs/openid-4-verifiable-presentations-1_0-20.html#section-6.4
 */
@AllArgsConstructor
public enum VerificationClientError {

    INVALID_SCOPE("invalid_scope"),
    INVALID_REQUEST("invalid_request"),
    INVALID_CLIENT("invalid_client"),
    VP_FORMATS_NOT_SUPPORTED("vp_formats_not_supported"),
    INVALID_PRESENTATION_DEFINITION_URI("invalid_presentation_definition_uri"),
    INVALID_PRESENTATION_DEFINITION_REFERENCE("invalid_presentation_definition_reference");

    private final String displayName;

    @JsonValue
    @Override
    public String toString() {
        return this.displayName;
    }
}
