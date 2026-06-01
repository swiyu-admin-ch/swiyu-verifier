package ch.admin.bj.swiyu.verifier.domain.vqps;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link Vqps} entities, keyed by {@code queryHash}.
 *
 * <p>Use {@link #findById(Object)} with the SHA-256 hash of the current DCQL query and metadata
 * to perform an exact cache lookup. The inherited {@code save()} method inserts or replaces
 * the row for a given hash.</p>
 */
public interface VqpsRepository extends JpaRepository<Vqps, String> {
}
