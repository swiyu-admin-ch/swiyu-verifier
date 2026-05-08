package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.core.trust.client.api.TrustProtocol20Api;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.nimbusds.jwt.JWTParser;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service that fetches and caches Trust Protocol 2.0 trust statements ({@code idTS} and {@code pvaTS})
 * from the Trust Registry sidechannel API.
 *
 * <p>Each trust statement JWT is cached until its individual {@code exp} claim expires,
 * implementing dynamic per-entry TTL via Caffeine's {@link Expiry} interface.
 * Spring's {@code @Cacheable} is intentionally <b>not</b> used here because it only supports
 * a static TTL per cache, not a dynamic per-entry TTL.</p>
 *
 * <p>API failures and empty responses are negatively cached (via {@code Optional.empty()})
 * for a short duration to prevent retry storms when the Trust Registry is unavailable.</p>
 *
 * <p>Only active when {@code swiyu.trust-registry.api-url} is configured.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "swiyu.trust-registry", name = "api-url")
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
     * Two separate caches keyed by their respective identifiers.
     * Separate caches ensure that invalidating one type does not affect the other.
     */
    private final Cache<String, Optional<String>> idTsCache;
    private final Cache<String, Optional<String>> pvaTsCache;

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
        this.idTsCache = buildCache();
        this.pvaTsCache = buildCache();
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
     * Returns the cached {@code pvaTS} (Protected Verification Authorization Trust Statement) JWT
     * for the configured JTI, fetching it from the trust registry on a cache miss.
     *
     * <p>Phase 2 (signature verification) is performed before returning the value to the caller.</p>
     *
     * @return the {@code pvaTS} JWT string, or {@code null} if unavailable or validation fails
     */
    @Nullable
    public String getProtectedVerificationAuthorizationTrustStatement(String verifierDid) {
        Optional<String> cached = pvaTsCache.get(verifierDid, this::fetchProtectedVerificationAuthorizationTrustStatement);
        return cached.isPresent() ? cached.orElse(null) : null;
    }

    // -------------------------------------------------------------------------
    // Cache invalidation
    // -------------------------------------------------------------------------

    /**
     * Immediately invalidates the cached {@code idTS} for the configured verifier DID,
     * forcing a fresh fetch on the next request.
     */
    public void invalidateIdentityTrustStatement() {
        log.info("Invalidating cached idTS for did={}", properties.getVerifierDid());
        idTsCache.invalidate(properties.getVerifierDid());
    }

    /**
     * Immediately invalidates the cached {@code pvaTS} for the configured JTI,
     * forcing a fresh fetch on the next request.
     */
    public void invalidateProtectedVerificationAuthorizationTrustStatement() {
        log.info("Invalidating cached pvaTS for jti={}", properties.getPvaTsJti());
        pvaTsCache.invalidate(properties.getPvaTsJti());
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
     * @param type      the statement type label for logging ("idTS" or "piaTS")
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

    private Optional<String> fetchProtectedVerificationAuthorizationTrustStatement(String verifierDid) {
        try {
            var response = trustProtocol20Api.listPvaTS(verifierDid, true, null, null, null).block();
            String jwt = extractFirstJwt(response != null ? response.getContent() : null, "piaTS", verifierDid);
            if (jwt != null) {
                validateTrustStatement(jwt, "pvaTS", verifierDid);
            }
            return Optional.ofNullable(jwt);
        } catch (JwtValidatorException e) {
            log.warn("pvaTS signature validation failed for verifier {}: {}", verifierDid, e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("API or network error fetching piaTS for verifier {}: {}", verifierDid, e.getMessage());
            return Optional.empty();
        }

    }

    @Nullable
    private String extractFirstJwt(@Nullable List<String> content, String type, String issuerDid) {
        if (content == null || content.isEmpty()) {
            log.warn("No {} trust statement found for issuer {}", type, issuerDid);
            return null;
        }
        return content.getFirst();
    }

    /**
     * Builds a Caffeine cache with dynamic per-entry TTL derived from the JWT {@code exp} claim.
     */
    private Cache<String, Optional<String>> buildCache() {
        return Caffeine.newBuilder()
                .maximumSize(properties.getMaxCacheSize())
                .expireAfter(buildExpiry())
                .build();
    }

    /**
     * Returns a Caffeine {@link Expiry} that derives the per-entry TTL from the JWT {@code exp} claim.
     * Negative entries ({@code Optional.empty()}) use a short fixed TTL to prevent retry storms.
     * On read, the remaining duration is preserved unchanged.
     */
    private Expiry<String, Optional<String>> buildExpiry() {
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
     * Parses the JWT to extract the {@code exp} claim and computes the remaining lifetime
     * in nanoseconds, minus the configured clock-skew buffer.
     * If {@code maxCacheTtlSeconds} is set, the TTL is additionally capped.
     */
    private long computeNanosUntilExpiry(String jwt) {
        return extractExpSeconds(jwt)
                .map(exp -> {
                    long remainingSeconds = (exp - properties.getClockSkewBufferSeconds()) - Instant.now().getEpochSecond();
                    if (remainingSeconds <= 0) {
                        log.warn("Trust statement JWT expires too soon or is already expired (exp={})", exp);
                        return TimeUnit.SECONDS.toNanos(1);
                    }
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
}
