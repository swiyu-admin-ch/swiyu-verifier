/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api.requestobject;

import ch.admin.bj.swiyu.verifier.api.definition.PresentationDefinitionDto;
import ch.admin.bj.swiyu.verifier.api.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.api.management.dcql.DcqlQueryDto;
import ch.admin.bj.swiyu.verifier.api.metadata.OpenidClientMetadataDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Schema(name = "RequestObject", description = """
    OID4VP Request Object sent to the Wallet as response after receiving an Authorization Request.
    Contains either a Presentation Exchange (PE) presentation_definition or a DCQL query (dcql_query), depending on the verification request format.
    - If the verification was initiated using the older PE format, the presentation_definition field is used.
    - If the verification was initiated using the new DCQL format, the dcql_query field is used.
    """)
@AllArgsConstructor
@NoArgsConstructor
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
    @Schema(description = """
            If "direct_post", expect the response to be sent via an HTTPS connection to response_uri.
            If "direct_post.jwt" expects the response to also be encrypted.
            """)
    private ResponseModeTypeDto responseMode;

    @JsonProperty("response_uri")
    private String responseUri;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("version")
    private String version;

    @JsonProperty("presentation_definition")
    @Schema(
        description = """
            Presentation definition according to https://identity.foundation/presentation-exchange/#presentation-definition.
            This field is only used for requests initiated with the older Presentation Exchange (PE) format.
            """,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private PresentationDefinitionDto presentationDefinition;

    @JsonProperty("dcql_query")
    @Schema(
        description = """
            DCQL query object as an Authorization Request parameter.
            This field is used for requests initiated with the DCQL format and contains the Digital Credentials Query Language (DCQL) query.
            """,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private DcqlQueryDto dcqlQuery;

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
                        "client_logo#fr": "www.example.com/logo_fr.png",
                        "vp_formats": {
                            "jwt_vp": {
                                "alg": [
                                    "ES256"
                                ],
                            }
                        }
                    }""")
    private OpenidClientMetadataDto clientMetadata;

    @JsonProperty("state")
    private String state;
}