package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * OID4VP Request Object send to the Wallet as response after receiving an Authorization Request.
 * Should be sent as JWT signed by the verifier.
 * The public key should be accessible using client_id
 * <a href="https://www.rfc-editor.org/rfc/rfc9101.html#name-request-object-2">Spec for Request Object</a>
 * <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-aud-of-a-request-object">OID4VP Changes to RequestObject</a>
 *
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestObject {

    /**
     * Source of the key material to verify the request object jwt
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

    @JsonProperty("presentation_definition")
    private List<InputDescriptor> inputDescriptors;

    @JsonProperty("client_metadata")
    private VerifierMetadata clientMetadata;

    @JsonProperty("state")
    private String state;
}
