package ch.admin.bj.swiyu.verifier.domain.management.dcql;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Domain model for DCQL Credential Query.
 * Represents a single credential query within a DCQL query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcqlCredential {

    /**
     * Unique identifier for this credential query within the DCQL query.
     */
    @JsonProperty("id")
    @NotEmpty
    private String id;

    /**
     * The format of the requested credential (e.g., "dc+sd-jwt", "jwt_vc_json").
     * Do not use for verification before EIDOMNI-284 is done
     */
    @JsonProperty("format")
    @NotEmpty
    private String format;

    /**
     * Metadata constraints for the credential.
     */
    @JsonProperty("meta")
    @NotNull
    private DcqlCredentialMeta meta;

    /**
     * List of claims requested from this credential.
     */
    @JsonProperty("claims")
    @Nullable
    private List<DcqlClaim> claims;

    /**
     * Whether cryptographic holder binding is required for this credential.
     */
    @JsonProperty("require_cryptographic_holder_binding")
    @Nullable
    private Boolean requireCryptographicHolderBinding;

    /**
     * Whether multiple instances of this credential are allowed.
     */
    @JsonProperty("multiple")
    @Nullable
    private Boolean multiple;
}
