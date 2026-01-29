package ch.admin.bj.swiyu.verifier.dto.metadata;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Data
@Schema(description =
        """
                Verifier metadata values.
                Additional Verifier metadata parameters MAY be defined and used, as described in [RFC7591].
                The Wallet MUST ignore any unrecognized parameters.""")
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class OpenidClientMetadataDto {

    @NotBlank
    @JsonProperty("client_id")
    private String clientId;

    /**
     * @deprecated replaced by vp_formats_supported in OID4VP 1.0
     */
    @NotNull
    @Valid
    @JsonProperty("vp_formats")
    @Deprecated(since="OID4VP 1.0")
    private OpenIdClientMetadataVpFormat vpFormats;

    private String version;

    @JsonProperty("jwks")
    @Schema(description = """
            One or more public keys, such as those used by the Wallet as an input to a key agreement
            that may be used for encryption of the Authorization Response, or where the Wallet will
            require the public key of the Verifier to generate a Verifiable Presentation.
            
            Public keys included in this parameter MUST NOT be used to verify the signature of signed Authorization Requests
            """)
    private JwkSetDto jwks;

    @JsonProperty(value = "encrypted_response_enc_values_supported", defaultValue = """
            ["A128GCM"]
            """)
    @Schema(description = """
           If present, should be non-empty array of JWE algorithms as in RFC7516.
           When a response_mode requiring encryption of the Response (such as direct_post.jwt) is specified,
           one of the specified algorithms is to be used.""")
    @Builder.Default
    private List<String> encryptedResponseEncValuesSupported = List.of("A128GCM");

    @JsonProperty(value = "vp_formats_supported", defaultValue =
            """
            "dc+sd-jwt": {
                    "sd-jwt_alg_values": ["ES256"],
                    "kb-jwt_alg_values": ["ES256"]
                  }
            """)
    @Schema(description = """
            An object containing a list of name/value pairs, where the name is a Credential Format Identifier and the
            value defines format-specific parameters that a Verifier supports.
            """)
    private OpenIdClientMetadataVpFormatsSupported vpFormatsSupported;

    // Dynamic fields
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnySetter
    public void setAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}