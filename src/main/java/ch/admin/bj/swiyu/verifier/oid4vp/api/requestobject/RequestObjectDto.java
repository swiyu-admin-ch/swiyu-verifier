/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.api.requestobject;

import ch.admin.bj.swiyu.verifier.oid4vp.api.definition.PresentationDefinitionDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * OID4VP Request Object sent to the Wallet as response after receiving an Authorization Request.
 * Should be sent as JWT signed by the verifier.
 * The public key should be accessible using client_id
 * <a href="https://www.rfc-editor.org/rfc/rfc9101.html#name-request-object-2">Spec for Request Object</a>
 * <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-aud-of-a-request-object">OID4VP Changes to RequestObjectDto</a>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RequestObject")
public class RequestObjectDto {

    /**
     * Oauth2 client_id for the oauth client (holder)
     * <a href="https://www.rfc-editor.org/rfc/rfc9101.html">RFC 9101</a>
     */
    @JsonProperty("client_id")
    private String clientId;

    /**
     * information on how the client_id has to be interpreted.
     * For our purposes will be likely always did
     */
    @JsonProperty("client_id_scheme")
    private String clientIdScheme;

    @JsonProperty("response_type")
    private String responseType;

    @JsonProperty("response_mode")
    private String responseMode;

    @JsonProperty("response_uri")
    private String responseUri;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("version")
    private String version;

    @JsonProperty("presentation_definition")
    private PresentationDefinitionDto presentationDefinition;

    @JsonProperty("client_metadata")
    @Schema(description = "A JSON object containing the Verifier metadata values providing further information about the verifier, such as name and logo. It is UTF-8 encoded. It MUST NOT be present if client_metadata_uri parameter is present.",
            example = """
                    {
                        "client_id": "did:example:12345",
                        "client_name#en": "English name (all regions)",
                        "client_name#fr": "French name (all regions)",
                        "client_name#de-DE": "German name (region Germany)",
                        "client_name#de-CH": "German name (region Switzerland)",
                        "client_name#de": "German name (fallback)",
                        "client_name": "Fallback name",
                        "client_logo": "www.example.com/logo.png",
                        "client_logo#fr": "www.example.com/logo_fr.png"
                    }""")
    private Map<String, Object> clientMetadata;

    @JsonProperty("state")
    private String state;
}
