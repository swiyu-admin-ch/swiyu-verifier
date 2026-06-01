package ch.admin.bj.swiyu.verifier.dto.requestobject;

import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlQueryDto;
import ch.admin.bj.swiyu.verifier.dto.metadata.OpenidClientMetadataDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OID4VP Request Object sent to the Wallet as response after receiving an Authorization Request.
 * Should be sent as JWT signed by the verifier.
 * The public key should be accessible using client_id
 * <a href="https://www.rfc-editor.org/rfc/rfc9101.html#name-request-object-2">Spec for Request Object</a>
 * <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-aud-of-a-request-object">OID4VP Changes to RequestObjectDto</a>
 */
@Data
@Builder(toBuilder = true)
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

    @JsonProperty("dcql_query")
    @Schema(
        description = """
            DCQL query object as an Authorization Request parameter.
            This field is used for requests initiated with the DCQL format and contains the Digital Credentials Query Language (DCQL) query.
            Mutually exclusive with the {@code scope} parameter: only one of the two may be present.
            """,
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private DcqlQueryDto dcqlQuery;

    /**
     * OAuth2 scope parameter. When a vqPS is present in {@code verifier_info}, this field
     * MUST be set to the scope registered in the vqPS and {@code dcql_query} MUST be omitted.
     * Mutually exclusive with {@code dcql_query}.
     */
    @JsonProperty("scope")
    @Schema(description = """
            OAuth2 scope value identifying the DCQL query registered in the vqPS.
            MUST be present when a vqPS is injected into verifier_info.
            Mutually exclusive with dcql_query.
            """,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String scope;

    @JsonProperty("client_metadata")
    @Schema(description = "A JSON object containing the Verifier metadata values providing further information about the verifier, such as name and logo. It is UTF-8 encoded. It MUST NOT be present if client_metadata_uri parameter is present.",
            example = """
                    {
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
    @Schema(description = """
            An opaque value used by the client to maintain
            state between the request and callback.  The authorization
            server includes this value when redirecting the user-agent back
            to the client.
            """)
    private String state;

    @JsonProperty("aud")
    private String audience;

    /**
     * Trust Protocol 2.0: array of trust statements ({@code idTS}, {@code pvaTS}, {@code vqPS})
     * injected into the JWT-Secured Authorization Request.
     * Each entry has the form {@code {"format": "jwt", "data": "<jwt-string>"}}.
     * This field is omitted when no trust statements are available (TP2.0 disabled or fetch failure).
     */
    @JsonProperty("verifier_info")
    @Schema(description = "Trust Protocol 2.0 trust statements embedded as JWT objects.")
    private List<VerifierInfoEntryDto> verifierInfo;
}