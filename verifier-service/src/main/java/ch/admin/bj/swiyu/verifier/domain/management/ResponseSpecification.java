package ch.admin.bj.swiyu.verifier.domain.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
// JSON-PERSISTED (ZDD): serialized to JSON in the "management" table (see Management.responseSpecification).
// Keep this type backward compatible across releases: don't rename/remove fields without a migration
// path (e.g. @JsonAlias), and keep any new field optional with a default.
@JsonIgnoreProperties(ignoreUnknown = true)
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
