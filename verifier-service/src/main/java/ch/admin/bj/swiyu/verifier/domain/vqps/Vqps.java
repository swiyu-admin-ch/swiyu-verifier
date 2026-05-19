package ch.admin.bj.swiyu.verifier.domain.vqps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Persistent cache entry for a Verification Query Public Statement (vqPS).
 *
 * <p>The primary key is {@code queryHash} – a SHA-256 hex digest of the canonical combination of
 * DCQL query, purpose name and purpose description. This design allows multiple vqPS entries per
 * {@code scope} (e.g. after a query change) and ensures that a cache hit is only returned when
 * the current request's query and metadata exactly match what was signed by the TMS. If the DCQL
 * query or localized metadata changes, the hash changes, a new row is inserted, and the TMS is
 * called again for re-registration.</p>
 *
 * <p>{@code scope} is stored as a plain column (not the PK) so that the service can look up
 * all entries for a given scope when evaluating cache validity.</p>
 */
@Entity
@Table(name = "vqps")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vqps {

    /**
     * SHA-256 hex digest of the canonical combination of DCQL query, purpose name and
     * purpose description. Used as the primary key so that each distinct query/metadata
     * combination has its own cache entry.
     */
    @Id
    @NotBlank
    @Column(name = "query_hash", nullable = false)
    private String queryHash;

    /**
     * Scope identifier linking this entry to a specific verification purpose.
     * Examples: {@code com.example.age_verification}.
     * Stored for logging and administrative visibility; not unique across rows.
     */
    @NotBlank
    @Column(nullable = false)
    private String scope;

    /**
     * Compact-serialized vqPS JWT as returned by the TMS.
     */
    @NotBlank
    @Column(nullable = false)
    private String jwt;

    /**
     * JWT expiry time in Unix epoch seconds (from the {@code exp} claim).
     * Used to determine whether the cached JWT is still valid for the current verification TTL.
     */
    @NotNull
    @Column(name = "expires_at", nullable = false)
    private long expiresAt;
}
