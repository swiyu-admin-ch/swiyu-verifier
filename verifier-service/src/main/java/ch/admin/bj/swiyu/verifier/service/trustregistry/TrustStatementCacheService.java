package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.core.trust.client.api.TrustProtocol20Api;
import ch.admin.bj.swiyu.core.trust.client.model.PagedModelString;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.nimbusds.jwt.JWTParser;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service that fetches and caches Trust Protocol 2.0 trust statements ({@code idTS} and {@code pvaTS})
 * from the Trust Registry sidechannel API.
 *
 * <p>The {@code idTS} cache stores a single JWT per issuer DID, whereas the {@code pvaTS} cache
 * stores a <em>list</em> of JWTs per verifier DID (1:N relationship). Each {@code pvaTS} authorizes
 * a different set of protected claims ({@code authorized_fields}), so all statements must be
 * available to cover any requested field combination.</p>
 *
 * <p>Dynamic per-entry TTL is implemented via Caffeine's {@link Expiry} interface.
 * For the {@code pvaTS} list the TTL is derived from the <em>minimum</em> {@code exp} claim
 * across all JWTs in the list, ensuring the entire list is evicted as soon as the earliest
 * statement expires.</p>
 *
 * <p>Spring's {@code @Cacheable} is intentionally <b>not</b> used here because it only supports
 * a static TTL per cache, not a dynamic per-entry TTL.</p>
 *
 * <p>API failures and empty responses are negatively cached for a short duration
 * to prevent retry storms when the Trust Registry is unavailable.</p>
 *
 * <p>Only active when {@code swiyu.trust-registry.api-url} is configured.</p>
 */
@Slf4j
@Service
@ConditionalOnExpression("'${swiyu.trust-registry.api-url:}'.length() > 0")
public class TrustStatementCacheService {

    /**
     * Fallback TTL in seconds used when the JWT {@code exp} claim cannot be parsed.
     */
    private static final long FALLBACK_TTL_SECONDS = 60;

    /**
     * Negative cache TTL in seconds applied when the TMS API returns empty or fails.
     * Prevents retry storms within this window.
     */
    private static final long NEGATIVE_CACHE_TTL_SECONDS = 30;

    private final TrustProtocol20Api trustProtocol20Api;
    private final TrustRegistryProperties properties;
    private final CacheMaintenanceService cacheMaintenanceService;

    /**
     * Optional validator for trust statement allowlist and signature checks.
     * When absent, Phase 1 validation is skipped and a warning is logged.
     */
    private final Optional<TrustStatementValidator> trustStatementValidator;

    /**
     * Cache for {@code idTS} JWTs, keyed by issuer DID.
     * Stores a single Optional JWT per issuer.
     */
    private final Cache<String, Optional<String>> idTsCache;

    /**
     * Cache for {@code pvaTS} JWT lists, keyed by verifier DID.
     * Stores all active pvaTS JWTs for the verifier (1:N).
     * {@code Optional.empty()} signals a negative cache entry: the TMS was reachable but
     * returned no results; the next fetch will be suppressed until the negative TTL expires.
     */
    private final Cache<String, Optional<List<String>>> pvaTsCache;

    /**
     * Constructs the cache service with injected API client and configuration.
     *
     * @param trustProtocol20Api      generated API client for the trust sidechannel
     * @param properties              trust registry configuration for cache tuning
     * @param cacheMaintenanceService service for evicting related Spring-managed caches on failures
     * @param trustStatementValidator optional allowlist/signature validator
     */
    public TrustStatementCacheService(TrustProtocol20Api trustProtocol20Api,
                                      TrustRegistryProperties properties,
                                      CacheMaintenanceService cacheMaintenanceService,
                                      Optional<TrustStatementValidator> trustStatementValidator) {
        this.trustProtocol20Api = trustProtocol20Api;
        this.properties = properties;
        this.cacheMaintenanceService = cacheMaintenanceService;
        this.trustStatementValidator = trustStatementValidator;
        this.idTsCache = buildIdTSCache();
        this.pvaTsCache = buildPvaTsCache();
    }


    /**
     * Retrieves every Trust Protocol 2.0 issuance statement that is relevant for the given
     * {@code issuerDid}.  The method performs a single {@link Mono#zip} call that concurrently
     * invokes the four side‑channel endpoints:
     * <ul>
     *   <li>{@code GET /idTS/{issuerDid}}</li>
     *   <li>{@code GET /activePiTLS}</li>
     *   <li>{@code GET /activeNcTLS}</li>
     *   <li>{@code GET /listPiaTS}</li>
     * </ul>
     *
     * <p>The response of {@code listPiaTS} is a {@link PagedModelString} containing a list of
     * JWT strings, while the other three endpoints return a plain {@link String} JWT.  This
     * method flattens all returned JWTs into a single {@link List<String>} preserving no
     * particular order.</p>
     *
     * @param issuerDid the DID of the credential issuer for which issuance statements are required
     * @return a mutable {@link List} containing the {@code idTS}, {@code piTLS}, {@code ncTLS}
     *         JWTs and all JWTs returned by {@code listPiaTS}; the list may be empty if every
     *         call returns {@code null} or an empty page
     */
    public List<String> getAllIssuanceStatementsFor(String issuerDid) {
        log.trace("Fetching trust statements related to issuance for {}", issuerDid);
        var responses = Mono.zip(
                trustProtocol20Api.getIdTS(issuerDid).defaultIfEmpty(""),
                trustProtocol20Api.getActivePiTLS().defaultIfEmpty(""),
                trustProtocol20Api.getActiveNcTLS().defaultIfEmpty(""),
                trustProtocol20Api.listPiaTS(issuerDid, true, null, null, null)
                        .defaultIfEmpty(new PagedModelString().content(List.of())))
                .block();

        List<String> statements = new LinkedList<>();
        Iterator<Object> it = responses.iterator();
        while(it.hasNext()) {
            Object response = it.next();
            if (response instanceof PagedModelString pageModelString) {
                statements.addAll(getListOfStatements(pageModelString));
            } 
            if (response instanceof String statement) {
                statements.add(statement);
            }
        }
        log.debug("Found a total of {} trust statements for issuer {}", statements.size(), issuerDid);
        return statements;
    }


    /**
     * Extracts the list of JWT strings from a {@link PagedModelString}.  If the supplied
     * {@code pagedModelString} is {@code null} or its {@code content} property is {@code null},
     * an empty immutable list is returned.
     *
     * @param pagedModelString the model object returned by the Trust Registry API; may be {@code null}
     * @return an immutable list of JWTs contained in the model, or an empty list if none are present
     */
    private List<String> getListOfStatements(PagedModelString pagedModelString) {
        if (pagedModelString == null || pagedModelString.getContent() == null) {
            return List.of();
        }
        return pagedModelString.getContent();
    }

    /**
     * Returns the cached Identity Trust Statement (idTS) JWT for the given issuer DID,
     * fetching it from the trust registry if not yet cached or already expired.
     *
     * @param issuerDid the effective issuer DID for which to retrieve the trust statement
     * @return the idTS JWT string, or {@code null} if unavailable
     */
    @Nullable
    public String getIdentityTrustStatement(String issuerDid) {
        Optional<String> cached = idTsCache.get(issuerDid, this::fetchIdentityTrustStatement);
        return cached.isPresent() ? cached.orElse(null) : null;
    }

    /**
     * Returns all cached {@code pvaTS} (Protected Verification Authorization Trust Statement) JWTs
     * for the given verifier DID, fetching them from the trust registry on a cache miss.
     *
     * <p>A verifier may hold multiple {@code pvaTS} JWTs, each authorizing a different set of
     * protected claims ({@code authorized_fields}). The caller is responsible for selecting
     * the relevant statements based on the requested fields.</p>
     *
     * <p>Returns an empty list when the TMS is unavailable or returned no results.</p>
     *
     * @param verifierDid the verifier DID whose pvaTS statements should be retrieved
     * @return a non-null, possibly empty list of pvaTS JWT strings
     */
    public List<String> getProtectedVerificationAuthorizationTrustStatements(String verifierDid) {
        Optional<List<String>> cached = pvaTsCache.get(verifierDid, this::fetchProtectedVerificationAuthorizationTrustStatements);
        return cached.orElse(List.of());
    }

    private Optional<String> fetchIdentityTrustStatement(String issuerDid) {
        try {
            String jwt = trustProtocol20Api.getIdTS(issuerDid).block();
            if (jwt == null) {
                log.warn("No idTS trust statement found for issuer {}", issuerDid);
            } else {
                validateTrustStatement(jwt, "idTS", issuerDid);
            }
            return Optional.ofNullable(jwt);
        } catch (JwtValidatorException e) {
            log.warn("idTS signature validation failed for issuer {}: {}", issuerDid, e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Failed to fetch idTS for issuer {}: {}", issuerDid, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Runs the pre-cache allowlist check via {@link TrustStatementValidator} (no HTTP call).
     * If no validator is configured, the check is skipped and a warning is logged.
     *
     * @param jwt       the trust statement JWT to check
     * @param type      the statement type label for logging ("idTS" or "pvaTS")
     * @param issuerDid the issuer DID for logging context
     * @throws JwtValidatorException if the DID URL resolved from the JWT is not on the allowlist
     */
    private void validateTrustStatement(String jwt, String type, String issuerDid) {
        if (trustStatementValidator.isEmpty()) {
            log.warn("No TrustStatementValidator configured – skipping allowlist check for {} of issuer {}", type, issuerDid);
            return;
        }
        trustStatementValidator.get().validateAllowlist(jwt);
        log.debug("{} allowlist check passed for issuer {}", type, issuerDid);
    }

    /**
     * Fetches all active {@code pvaTS} JWTs for the given verifier DID from the Trust Registry.
     * Performs the pre-cache allowlist check on each fetched JWT.
     * Returns {@code Optional.empty()} on API failure or when the TMS returns no results
     * (negative caching), so the cache can distinguish "never fetched" from "fetched but empty".
     *
     * @param verifierDid the verifier DID to query
     * @return an Optional wrapping the list of validated pvaTS JWT strings;
     *         {@code Optional.empty()} for negative cache entries
     */
    private Optional<List<String>> fetchProtectedVerificationAuthorizationTrustStatements(String verifierDid) {
        try {
            var response = trustProtocol20Api.listPvaTS(verifierDid, true, null, null, null).block();
            List<String> jwts = getListOfStatements(response);
            if (jwts.isEmpty()) {
                log.warn("No pvaTS trust statements found for verifier {}", verifierDid);
                return Optional.empty();
            }
            log.debug("Fetched {} pvaTS JWT(s) for verifier {}", jwts.size(), verifierDid);
            jwts.forEach(jwt -> validateTrustStatement(jwt, "pvaTS", verifierDid));
            return Optional.of(List.copyOf(jwts));
        } catch (JwtValidatorException e) {
            log.warn("pvaTS allowlist validation failed for verifier {}: {}", verifierDid, e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("API or network error fetching pvaTS for verifier {}: {}", verifierDid, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Builds a Caffeine cache for single-JWT entries ({@code idTS}) with dynamic TTL
     * derived from the JWT {@code exp} claim.
     */
    private Cache<String, Optional<String>> buildIdTSCache() {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMaxCacheSize())
                .expireAfter(buildExpiry())
                .build();
    }

    private @NonNull Expiry<String, Optional<String>> buildExpiry() {
        return new Expiry<>() {
            @Override
            public long expireAfterCreate(String key, Optional<String> jwtOpt, long currentTime) {
                return jwtOpt.map(TrustStatementCacheService.this::computeNanosUntilExpiry)
                        .orElseGet(() -> TimeUnit.SECONDS.toNanos(NEGATIVE_CACHE_TTL_SECONDS));
            }

            @Override
            public long expireAfterUpdate(String key, Optional<String> jwtOpt, long currentTime, long currentDuration) {
                return jwtOpt.map(TrustStatementCacheService.this::computeNanosUntilExpiry)
                        .orElseGet(() -> TimeUnit.SECONDS.toNanos(NEGATIVE_CACHE_TTL_SECONDS));
            }

            @Override
            public long expireAfterRead(String key, Optional<String> jwtOpt, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };
    }

    /**
     * Builds a Caffeine cache for {@code pvaTS} JWT lists with dynamic TTL.
     * The TTL of the list is the <em>minimum</em> remaining lifetime across all JWTs in the list,
     * so the list is evicted and re-fetched as soon as the earliest statement expires.
     * {@code Optional.empty()} (negative cache) uses a short fixed TTL.
     */
    private Cache<String, Optional<List<String>>> buildPvaTsCache() {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMaxCacheSize())
                .expireAfter(new Expiry<String, Optional<List<String>>>() {
                    @Override
                    public long expireAfterCreate(String key, Optional<List<String>> jwtsOpt, long currentTime) {
                        return computeNanosUntilListExpiry(jwtsOpt);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Optional<List<String>> jwtsOpt, long currentTime, long currentDuration) {
                        return computeNanosUntilListExpiry(jwtsOpt);
                    }

                    @Override
                    public long expireAfterRead(String key, Optional<List<String>> jwtsOpt, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * Computes the cache TTL for an Optional list of pvaTS JWTs.
     * For a present list the TTL is the minimum remaining lifetime across all entries.
     * For {@code Optional.empty()} (negative cache entry) the short negative-cache TTL is used.
     *
     * @param jwtsOpt the Optional list of JWT strings
     * @return TTL in nanoseconds
     */
    private long computeNanosUntilListExpiry(Optional<List<String>> jwtsOpt) {
        return jwtsOpt.map(jwts -> jwts.stream()
                        .mapToLong(this::computeNanosUntilExpiry)
                        .min()
                        .orElseGet(() -> TimeUnit.SECONDS.toNanos(FALLBACK_TTL_SECONDS)))
                .orElseGet(() -> TimeUnit.SECONDS.toNanos(NEGATIVE_CACHE_TTL_SECONDS));
    }

    /**
     * Parses the JWT payload to extract the {@code exp} claim and computes
     * the remaining lifetime in nanoseconds, minus a clock-skew buffer.
     *
     * <p>If {@code maxCacheTtlSeconds} is configured, the effective TTL is
     * {@code min(exp-based TTL, maxCacheTtlSeconds)} – this allows aligning the
     * trust statement cache with the DID public key cache TTL to avoid serving
     * statements whose referenced DID key has already been rotated.</p>
     *
     * <p>If parsing fails, {@link #FALLBACK_TTL_SECONDS} is used as fallback.</p>
     *
     * @param jwt the serialized JWT string
     * @return remaining lifetime in nanoseconds (minimum 1 second)
     */
    private long computeNanosUntilExpiry(String jwt) {
        return extractExpSeconds(jwt)
                .map(exp -> {
                    long remainingSeconds = (exp - properties.getClockSkewBufferSeconds()) - Instant.now().getEpochSecond();
                    if (remainingSeconds <= 0) {
                        log.warn("Trust statement JWT expires too soon or is already expired (exp={})", exp);
                        return TimeUnit.SECONDS.toNanos(1);
                    }
                    // Apply optional hard upper bound
                    long maxTtl = properties.getMaxCacheTtlSeconds();
                    if (maxTtl > 0 && remainingSeconds > maxTtl) {
                        log.debug("Capping trust statement cache TTL at {}s (exp-based would be {}s)", maxTtl, remainingSeconds);
                        remainingSeconds = maxTtl;
                    }
                    log.debug("Caching trust statement JWT for {}s (exp={}, buffer={}s)",
                            remainingSeconds, exp, properties.getClockSkewBufferSeconds());
                    return TimeUnit.SECONDS.toNanos(remainingSeconds);
                })
                .orElseGet(() -> {
                    log.warn("Could not extract exp from trust statement JWT – using {}s fallback TTL", FALLBACK_TTL_SECONDS);
                    return TimeUnit.SECONDS.toNanos(FALLBACK_TTL_SECONDS);
                });
    }

    /**
     * Parses the JWT without signature verification and extracts the {@code exp} claim in epoch seconds.
     */
    private Optional<Long> extractExpSeconds(String jwt) {
        try {
            return Optional.ofNullable(JWTParser.parse(jwt).getJWTClaimsSet().getExpirationTime())
                    .map(date -> date.getTime() / 1000);
        } catch (ParseException e) {
            log.warn("Failed to parse JWT payload for exp extraction: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Invalidates all cached Trust Statements (idTS and pvaTS) for the given DID.
     *
     * <p>Convenience method combining both invalidations. Useful when a general
     * trust failure is detected and all statements for a DID should be refreshed.</p>
     *
     * <p>In addition, it triggers the clearing of the public key and encryption metadata
     * caches to ensure that potentially rotated keys are reloaded.</p>
     *
     * @param did the DID whose cached trust statements should be invalidated
     */
    public void invalidateAllTrustStatements(String did) {
        log.info("Invalidating all cached trust statements for DID {}", did);
        idTsCache.invalidate(did);
        pvaTsCache.invalidate(did);
        cacheMaintenanceService.evictJwkManually(did);
    }
}
