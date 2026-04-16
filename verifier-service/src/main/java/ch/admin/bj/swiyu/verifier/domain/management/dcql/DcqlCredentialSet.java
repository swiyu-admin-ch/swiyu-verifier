package ch.admin.bj.swiyu.verifier.domain.management.dcql;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Domain model for DCQL Credential Set Query.
 * Specifies additional constraints on which of the requested Credentials to return.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcqlCredentialSet {

    /**
     * Array of credential set options. Each option is an array of credential IDs.
     * At least one of the options must be satisfied.
     */
    @JsonProperty("options")
    private List<List<String>> options;

    /**
     * Whether this credential set is required.
     */
    @JsonProperty("required")
    private Boolean required;
}
