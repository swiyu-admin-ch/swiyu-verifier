package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.core.trust.client.api.TrustProtocol20Api;
import ch.admin.bj.swiyu.core.trust.client.model.PagedModelString;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service that fetches and caches Trust Protocol 2.0 trust statements
 * ({@code idTS} and {@code pvaTS})
 * from the Trust Registry sidechannel API.
 *
 * <p>
 * The {@code idTS} cache stores a single JWT per issuer DID, whereas the
 * {@code pvaTS} cache
 * stores a <em>list</em> of JWTs per verifier DID (1:N relationship). Each
 * {@code pvaTS} authorizes
 * a different set of protected claims ({@code authorized_fields}), so all
 * statements must be
 * available to cover any requested field combination.
 * </p>
 *
 * <p>
 * Dynamic per-entry TTL is implemented via Caffeine's {@link Expiry} interface.
 * For the {@code pvaTS} list the TTL is derived from the <em>minimum</em>
 * {@code exp} claim and {@code exp} or {@code ttl} of associated status 
 * across all JWTs in the list, ensuring the entire list is evicted as soon as
 * the earliest statement expires.
 * </p>
 *
 * <p>
 * Spring's {@code @Cacheable} is intentionally <b>not</b> used here because it
 * only supports
 * a static TTL per cache, not a dynamic per-entry TTL.
 * </p>
 *
 * <p>
 * API failures and empty responses are negatively cached for a short duration
 * to prevent retry storms when the Trust Registry is unavailable.
 * </p>
 *
 * <p>
 * Only active when {@code swiyu.trust-registry.api-url} is configured.
 * </p>
 */
@Slf4j
@Service
@ConditionalOnExpression("'${swiyu.trust-registry.api-url:}'.length() > 0")
public class TrustStatementCacheService {
    private final static String ACTIVE_TRUST_LIST_STATEMENT = "active_trust_list_statement";

    private final CacheProperties cacheProperties;
    private final TrustProtocol20Api trustProtocol20Api;
    private final TrustRegistryProperties properties;
    private final CacheMaintenanceService cacheMaintenanceService;

    private final TrustStatementValidator trustStatementValidator;

    /**
     * Cache for {@code idTS} JWTs, keyed by issuer DID.
     * Stores a single Optional JWT per issuer.
     */
    @Getter(value = AccessLevel.PROTECTED)
    private final Cache<String, ValidatedSingleTrustStatement> idTsCache;

    /**
     * Cache for {@code pvaTS} JWT lists, keyed by verifier DID.
     * Stores all active pvaTS JWTs for the verifier (1:N).
     * {@code Optional.empty()} signals a negative cache entry: the TMS was
     * reachable but
     * returned no results; the next fetch will be suppressed until the negative TTL
     * expires.
     */
    @Getter(value = AccessLevel.PROTECTED)
    private final Cache<String, List<ValidatedSingleTrustStatement>> pvaTsCache;

    private final Cache<String, ValidatedSingleTrustStatement> piTLSCache;
    private final Cache<String, ValidatedSingleTrustStatement> ncTLSCache;
    private final Cache<String, List<ValidatedSingleTrustStatement>> piaTsCache;

    /**
     * Constructs the cache service with injected API client and configuration.
     *
     * @param trustProtocol20Api      generated API client for the trust sidechannel
     * @param properties              trust registry configuration for cache tuning
     * @param cacheMaintenanceService service for evicting related Spring-managed
     *                                caches on failures
     * @param trustStatementValidator optional allowlist/signature validator
     */
    public TrustStatementCacheService(TrustProtocol20Api trustProtocol20Api,
            TrustRegistryProperties properties,
            CacheMaintenanceService cacheMaintenanceService,
            TrustStatementValidator trustStatementValidator, CacheProperties cacheProperties) {
        this.trustProtocol20Api = trustProtocol20Api;
        this.properties = properties;
        this.cacheMaintenanceService = cacheMaintenanceService;
        this.trustStatementValidator = trustStatementValidator;
        this.cacheProperties = cacheProperties;
        this.idTsCache = buildTrustStatementCache();
        this.pvaTsCache = buildTrustStatementListCache();
        this.piTLSCache = buildTrustStatementCache();
        this.ncTLSCache = buildTrustStatementCache();
        this.piaTsCache = buildTrustStatementListCache();
    }

    /**
     * Retrieves every Trust Protocol 2.0 issuance statement that is relevant for
     * the given
     * {@code issuerDid}. The method performs a single {@link Mono#zip} call that
     * concurrently
     * invokes the four side‑channel endpoints:
     * <ul>
     * <li>{@code GET /idTS/{issuerDid}}</li>
     * <li>{@code GET /activePiTLS}</li>
     * <li>{@code GET /activeNcTLS}</li>
     * <li>{@code GET /listPiaTS}</li>
     * </ul>
     *
     * <p>
     * The response of {@code listPiaTS} is a {@link PagedModelString} containing a
     * list of
     * JWT strings, while the other three endpoints return a plain {@link String}
     * JWT. This
     * method flattens all returned JWTs into a single {@link List<String>}
     * preserving no
     * particular order.
     * </p>
     *
     * @param issuerDid the DID of the credential issuer for which issuance
     *                  statements are required
     * @return a mutable {@link List} containing the {@code idTS}, {@code piTLS},
     *         {@code ncTLS}
     *         JWTs and all JWTs returned by {@code listPiaTS}; the list may be
     *         empty if every
     *         call returns {@code null} or an empty page
     */
    public List<String> getAllIssuanceStatementsFor(String issuerDid) {
        log.trace("Fetching trust statements related to issuance for {}", issuerDid);
        List<String> trustStatements = new LinkedList<>();
        trustStatements.add(getIdentityTrustStatement(issuerDid));
        trustStatements.add(getProtectedIssuanceTrustListStatement());
        trustStatements.add(getNonComplianceTrustListStatement());
        trustStatements.addAll(getProtectedIssuanceAuthorizationTrustStatements(issuerDid));
        return trustStatements.stream().filter(Objects::nonNull).toList();
    }

    /**
     * Returns the cached Identity Trust Statement (idTS) JWT for the given issuer
     * DID,
     * fetching it from the trust registry if not yet cached or already expired.
     *
     * @param did the effective issuer or verifier DID for which to retrieve the
     *            trust statement
     * @return the idTS JWT string, or {@code null} if unavailable
     */
    @Nullable
    public String getIdentityTrustStatement(String did) {
        ValidatedSingleTrustStatement cached = idTsCache.get(did, this::fetchIdentityTrustStatement);
        return cached != null && cached.trustStatement.isPresent() && cached.valid ? cached.trustStatement.orElse(null) : null;
    }

    @Nullable
    public String getProtectedIssuanceTrustListStatement() {
        ValidatedSingleTrustStatement cached = piTLSCache.get(ACTIVE_TRUST_LIST_STATEMENT,
                k -> this.fetchProtectedIssuanceTrustListStatement());
        return cached != null && cached.trustStatement.isPresent() && cached.valid ? cached.trustStatement.orElse(null) : null;
    }

    @Nullable
    public String getNonComplianceTrustListStatement() {
        ValidatedSingleTrustStatement cached = ncTLSCache.get(ACTIVE_TRUST_LIST_STATEMENT,
                k -> this.fetchNonComplianceTrustListStatement());
        return cached != null && cached.trustStatement.isPresent() && cached.valid ? cached.trustStatement.orElse(null) : null;
    }

    /**
     * Returns all cached {@code pvaTS} (Protected Verification Authorization Trust
     * Statement) JWTs
     * for the given verifier DID, fetching them from the trust registry on a cache
     * miss.
     *
     * <p>
     * A verifier may hold multiple {@code pvaTS} JWTs, each authorizing a different
     * set of
     * protected claims ({@code authorized_fields}). The caller is responsible for
     * selecting
     * the relevant statements based on the requested fields.
     * </p>
     *
     * <p>
     * Returns an empty list when the TMS is unavailable or returned no results.
     * </p>
     *
     * @param verifierDid the verifier DID whose pvaTS statements should be
     *                    retrieved
     * @return a non-null, possibly empty list of pvaTS JWT strings
     */
    public List<String> getProtectedVerificationAuthorizationTrustStatements(String verifierDid) {
        List<ValidatedSingleTrustStatement> statements = pvaTsCache.get(verifierDid, this::fetchProtectedVerificationAuthorizationTrustStatements);
        if (statements == null) {
            return List.of();
        }
        return statements
                .stream().map(vts -> vts.trustStatement)
                .filter(ts -> ts.isPresent())
                .map(ts -> ts.get()).toList();
    }

    public List<String> getProtectedIssuanceAuthorizationTrustStatements(String issuerDid) {
        List<ValidatedSingleTrustStatement> statements = piaTsCache.get(issuerDid, this::fetchProtectedIssuanceAuthorizationTrustStatements);
        if (statements == null) {
            return List.of();
        }
        return statements
                .stream().map(vts -> vts.trustStatement)
                .filter(ts -> ts.isPresent())
                .map(ts -> ts.get()).toList();
    }

    /**
     * Invalidates all cached Trust Statements (idTS and pvaTS) for the given DID.
     *
     * <p>
     * Convenience method combining both invalidations. Useful when a general
     * trust failure is detected and all statements for a DID should be refreshed.
     * </p>
     *
     * <p>
     * In addition, it triggers the clearing of the public key and encryption
     * metadata
     * caches to ensure that potentially rotated keys are reloaded.
     * </p>
     *
     * @param did the DID whose cached trust statements should be invalidated
     */
    public void invalidateAllTrustStatements(String did) {
        log.info("Invalidating all cached trust statements for DID {}", did);
        idTsCache.invalidate(did);
        pvaTsCache.invalidate(did);
        piaTsCache.invalidate(did);
        cacheMaintenanceService.evictJwkManually(did);
    }

    private ValidatedSingleTrustStatement fetchIdentityTrustStatement(String issuerDid) {
        try {
            String jwt = trustProtocol20Api.getIdTS(issuerDid).block();
            if (jwt == null) {
                log.warn("No idTS trust statement found for issuer {}", issuerDid);
            }
            return validateTrustStatement(jwt);
        } catch (RuntimeException e) {
            log.warn("Failed to fetch idTS for issuer {}: {}", issuerDid, e.getMessage());
            return null;
        }
    }

    private ValidatedSingleTrustStatement fetchProtectedIssuanceTrustListStatement() {
        try {
            String jwt = trustProtocol20Api.getActivePiTLS().block();
            if (jwt == null) {
                log.warn("No active protected issuance trust list statement found");
            }
            return validateTrustStatement(jwt);
        } catch (RuntimeException e) {
            log.warn("Failed to fetch piTLS", e.getMessage());
            return new ValidatedSingleTrustStatement(Optional.empty(), false, 0);
        }
    }

    private ValidatedSingleTrustStatement fetchNonComplianceTrustListStatement() {
        try {
            String jwt = trustProtocol20Api.getActiveNcTLS().block();
            if (jwt == null) {
                log.warn("No active non-compliance statement found");
            }
            return validateTrustStatement(jwt);
        } catch (RuntimeException e) {
            log.warn("Failed to fetch idTS for issuer {}", e.getMessage());
            return new ValidatedSingleTrustStatement(Optional.empty(), false, 0);
        }
    }

    /**
     * Fetches all active {@code pvaTS} JWTs for the given verifier DID from the
     * Trust Registry.
     * Performs the pre-cache allowlist check on each fetched JWT.
     * Returns {@code Optional.empty()} on API failure or when the TMS returns no
     * results
     * (negative caching), so the cache can distinguish "never fetched" from
     * "fetched but empty".
     *
     * @param verifierDid the verifier DID to query
     * @return an Optional wrapping the list of validated pvaTS JWT strings;
     *         {@code Optional.empty()} for negative cache entries
     */
    private List<ValidatedSingleTrustStatement> fetchProtectedVerificationAuthorizationTrustStatements(
            String verifierDid) {
        try {
            var response = trustProtocol20Api.listPvaTS(verifierDid, true, null, null, null).block();
            List<String> jwts = getListOfStatements(response);
            return jwts.stream()
                    .map(this::validateTrustStatement)
                    .filter(vts -> vts.valid)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("An error occured while fetching pvaTS for verifier {}: {}", verifierDid, e.getMessage());
            return null;
        }
    }

    private List<ValidatedSingleTrustStatement> fetchProtectedIssuanceAuthorizationTrustStatements(String issuerDid) {
        try {
            var response = trustProtocol20Api.listPiaTS(issuerDid, true, null, null, null).block();
            List<String> jwts = getListOfStatements(response);
            return jwts.stream()
                    .map(this::validateTrustStatement)
                    .filter(vts -> vts.valid)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("An error occured while fetching piaTS for issuer {}: {}", issuerDid, e.getMessage());
            return null; // Returning an empty list here will cause it to be cached for cacheProperties.getRequestBackoffSeconds()
        }
    }

    private ValidatedSingleTrustStatement validateTrustStatement(String tsJWT) {
        var validationResult = trustStatementValidator.trustStatementValidityWindow(tsJWT);
        return new ValidatedSingleTrustStatement(Optional.ofNullable(tsJWT), validationResult.isValid(),
                validationResult.valditiyWindow());
    }

    /**
     * Builds a Caffeine cache for single valid trust statement with dynamic TTL.
     * derived from the minimum of JWT {@code exp} claims and Status List TTL claim.
     */
    private Cache<String, ValidatedSingleTrustStatement> buildTrustStatementCache() {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMaxCacheSize())
                .expireAfter(buildSingleTrustStatementExpiry())
                .build();
    }

    private @NonNull Expiry<String, ValidatedSingleTrustStatement> buildSingleTrustStatementExpiry() {
        return new Expiry<>() {
            @Override
            public long expireAfterCreate(String key, ValidatedSingleTrustStatement ts, long currentTime) {
                return getValidTtlOrBackoff(ts);
            }

            @Override
            public long expireAfterUpdate(String key, ValidatedSingleTrustStatement ts, long currentTime,
                    long currentDuration) {
                return getValidTtlOrBackoff(ts);
            }

            @Override
            public long expireAfterRead(String key, ValidatedSingleTrustStatement ts, long currentTime,
                    long currentDuration) {
                return currentDuration;
            }

            private long getValidTtlOrBackoff(ValidatedSingleTrustStatement value) {
                return value.valid ? value.ttl : TimeUnit.SECONDS.toNanos(cacheProperties.getRequestBackoffSeconds());
            }
        };
    }

    /**
     * Builds a Caffeine cache for a lists of valid trust statements with dynamic
     * TTL.
     * The TTL of the list is the <em>minimum</em> remaining lifetime across all
     * JWTs and their Status Lists in the list,
     * so the list is evicted and re-fetched as soon as the earliest statement
     * expires.
     * Invalid Statements or no statments use a fixed TTL until fetch is
     * reattempted.
     */
    private Cache<String, List<ValidatedSingleTrustStatement>> buildTrustStatementListCache() {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMaxCacheSize())
                .expireAfter(buildListTrustStatementExpiry())
                .build();
    }

    private @NonNull Expiry<String, List<ValidatedSingleTrustStatement>> buildListTrustStatementExpiry() {
        return new Expiry<String, List<ValidatedSingleTrustStatement>>() {

            @Override
            public long expireAfterCreate(String key, List<ValidatedSingleTrustStatement> value, long currentTime) {
                return getValidTtlOrBackoff(value);
            }

            @Override
            public long expireAfterUpdate(String key, List<ValidatedSingleTrustStatement> value, long currentTime,
                    long currentDuration) {
                return getValidTtlOrBackoff(value);
            }

            @Override
            public long expireAfterRead(String key, List<ValidatedSingleTrustStatement> value, long currentTime,
                    long currentDuration) {
                return currentDuration;
            }

            /**
             * Cache the list of trust statements for the ttl. If no valid trust statement is found, cache the empty list for
             * backoff seconds to prevent spamming the registry
             * @param value the list of trust statements to be cached
             * @return cache duration in nanoseconds
             */
            private long getValidTtlOrBackoff(List<ValidatedSingleTrustStatement> value) {
                return value.stream()
                        .filter(v -> v.valid)
                        .mapToLong(v -> v.ttl)
                        .min()
                        .orElse(TimeUnit.SECONDS.toNanos(cacheProperties.getRequestBackoffSeconds()));
            }

        };
    }

    /**
     * Extracts the list of JWT strings from a {@link PagedModelString}. If the
     * supplied
     * {@code pagedModelString} is {@code null} or its {@code content} property is
     * {@code null},
     * an empty immutable list is returned.
     *
     * @param pagedModelString the model object returned by the Trust Registry API;
     *                         may be {@code null}
     * @return an immutable list of JWTs contained in the model, or an empty list if
     *         none are present
     */
    private List<String> getListOfStatements(PagedModelString pagedModelString) {
        if (pagedModelString == null || pagedModelString.getContent() == null) {
            return List.of();
        }
        return pagedModelString.getContent();
    }

    public record ValidatedSingleTrustStatement(@NonNull Optional<String> trustStatement, boolean valid, long ttl) {
    }
}
