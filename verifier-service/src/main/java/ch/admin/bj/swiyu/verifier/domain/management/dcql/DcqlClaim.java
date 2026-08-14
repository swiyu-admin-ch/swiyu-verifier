package ch.admin.bj.swiyu.verifier.domain.management.dcql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Domain model for DCQL Claim.
 * Represents a specific claim request within a credential query.
 */
// JSON-PERSISTED (ZDD): nested within DcqlQuery, which is serialized to JSON in the "management" table.
// Keep this type backward compatible across releases: don't rename/remove fields without a migration
// path (e.g. @JsonAlias), and keep any new field optional with a default.
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DcqlClaim {

    @JsonProperty("id")
    private String id;

    /**
     * JSON path to the claim within the credential.
     */
    @JsonProperty("path")
    private List<Object> path;

    /**
     * OPTIONAL A non-empty array of strings, integers or boolean values that specifies the expected values of the claim
     */
    @JsonProperty("values")
    List<Object> values;
}
