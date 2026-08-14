package ch.admin.bj.swiyu.verifier.domain.management.dcql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
// JSON-PERSISTED (ZDD): nested within DcqlQuery, which is serialized to JSON in the "management" table.
// Keep this type backward compatible across releases: don't rename/remove fields without a migration
// path (e.g. @JsonAlias), and keep any new field optional with a default.
@JsonIgnoreProperties(ignoreUnknown = true)
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
