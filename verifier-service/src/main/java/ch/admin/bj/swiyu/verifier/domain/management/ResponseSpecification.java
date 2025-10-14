package ch.admin.bj.swiyu.verifier.domain.management;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Collection of settings and data stored for the request object defining the expected wallet response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseSpecification {
    @Builder.Default
    @NotNull
    @JsonProperty("response_mode")
    private ResponseModeType responseModeType = ResponseModeType.DIRECT_POST;

    /**
     * List of json web keys to be provided as encryption option to the wallet
     */
    @Nullable
    @JsonProperty("jwks")
    private String jwks;

    /**
     * List of ephemeral (single-use) private json web keys
     */
    @Nullable
    @JsonProperty("jwks_private")
    private String jwksPrivate;

    @Nullable
    @JsonProperty("encrypted_response_enc_values_supported")
    private List<String> encryptedResponseEncValuesSupported;
}
