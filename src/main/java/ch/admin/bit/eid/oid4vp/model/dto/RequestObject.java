package ch.admin.bit.eid.oid4vp.model.dto;

import ch.admin.bit.eid.verifier_management.models.dto.ClientMetadataDto;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * OID4VP Request Object send to the Wallet as response after receiving an Authorization Request
 */
@Data
@Builder
public class RequestObject {

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("client_id_scheme")
    private String clientIdScheme;

    @JsonProperty("responseType")
    private String responseType = "vp_token";

    @JsonProperty("response_mode")
    private String responseMode = "direct_post";

    @JsonProperty("response_uri")
    private String responseUri;

    @JsonProperty("nonce")
    private String nonce;

    @JsonProperty("presentation_definition")
    private List<InputDescriptorDto> inputDescriptors;

    @JsonProperty("client_metadata")
    private ClientMetadataDto clientMetadata;

    @JsonProperty("state")
    private String state;
}
