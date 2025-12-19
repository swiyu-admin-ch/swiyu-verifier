package ch.admin.bj.swiyu.verifier.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;

/**
 * Allowed error responses the wallet can make according to <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#section-6.4">OpenID4VP</a>
 */
@AllArgsConstructor
@Schema(name = "VerificationClientError")
public enum VerificationClientErrorDto {

    // RFC Codes
    INVALID_SCOPE("invalid_scope"),
    INVALID_REQUEST("invalid_request"),
    INVALID_CLIENT("invalid_client"),
    VP_FORMATS_NOT_SUPPORTED("vp_formats_not_supported"),
    INVALID_PRESENTATION_DEFINITION_URI("invalid_presentation_definition_uri"),
    INVALID_PRESENTATION_DEFINITION_REFERENCE("invalid_presentation_definition_reference"),

    // Codes according to custom profile
    // TODO This is a custom legacy code which is still used on the wallet and
    //  should be replaced in the future by access_denied from a newer specification
    //  https://openid.net/specs/openid-4-verifiable-presentations-1_0-24.html#section-8.5-8
    CLIENT_REJECTED("client_rejected"),
    ACCESS_DENIED("access_denied");

    private final String jsonValue;

    @JsonValue
    @Override
    public String toString() {
        return this.jsonValue;
    }
}