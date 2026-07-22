package ch.admin.bj.swiyu.verifier.service.statuslist;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

import java.text.ParseException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.statuslist.TokenStatusListVerifier;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListMapper;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.verifier.common.config.CacheProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.util.time.TimeUtil;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.SignedJWT;

@Slf4j
@Service
public class StatusListCacheService {
    private final CacheProperties cacheProperties;
    private final DidKidParser didKidParser = new DidKidParser();
    private final DidJwtValidator didJwtValidator;
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListResolver statusListResolver;

    @Getter(value = AccessLevel.PROTECTED) // Allow Protected level access to cache for unit tests
    private final Cache<String, Optional<TokenStatusListTokenDto>> cache;


    


    public StatusListCacheService(CacheProperties cacheProperties, DidJwtValidator didJwtValidator,
            IssuerPublicKeyLoader issuerPublicKeyLoader, StatusListResolver statusListResolver) {
        this.cacheProperties = cacheProperties;
        this.didJwtValidator = didJwtValidator;
        this.issuerPublicKeyLoader = issuerPublicKeyLoader;
        this.statusListResolver = statusListResolver;
        this.cache = buildTokenStatusListTokenCache();
    }

    /**
     * Resolves the given URI to a verified TokenStatusListToken, caching it if possible to reduce load
     * @param uri URI where the status list is located
     * @return the TokenStatusListToken or null, if it cannot be resolved
     */
    public TokenStatusListTokenDto getTokenStatusListTokenByUri(String uri) {
        return cache.get(uri, this::resolveValidatedStatusList).orElseThrow(() -> 
            credentialError(VerificationErrorResponseCode.UNRESOLVABLE_STATUS_LIST, "Status List %s cannot be resolved".formatted(uri)));
    }

    /**
     * Fetches and validates the Token Status List found at URI. Validation is for it being a valid JWT and 
     * fulfilling the basic requirements of a token status list according to the spec.
     * @param uri URI where the status list is located
     * @return the TokenStatusListToken or null, if it cannot be resolved
     */
    private Optional<TokenStatusListTokenDto> resolveValidatedStatusList(String uri) {
        try {
        String statusListJWT = statusListResolver.resolveStatusList(uri);
        SignedJWT tokenStatusListJWT = SignedJWT.parse(statusListJWT);
        TokenStatusListVerifier.hasValidTokenStatusListTokenHeader(tokenStatusListJWT.getHeader());
        TokenStatusListTokenDto statusList = TokenStatusListMapper.toTokenStatusListToken(tokenStatusListJWT.getJWTClaimsSet().getClaims());
        String kid = didKidParser.extractKidFromHeader(statusListJWT);
        JWK statusListKey = issuerPublicKeyLoader.loadJWK(kid);
        didJwtValidator.validateJwt(statusListJWT, statusListKey);
        return Optional.of(statusList);
        } catch (StatusListFetchFailedException | IllegalArgumentException | ParseException | LoadingPublicKeyOfIssuerFailedException e) {
            log.info("Failed to load status list {}", uri, e);
            return Optional.empty();
        }
    }


    /**
     * Create a Caffeine cache for TokenStatusListTokens, taking the mimimum of expiry, ttl or a property ttl for cache lifetime duration
     * @return A new caffeine cache
     */
    private Cache<String, Optional<TokenStatusListTokenDto>> buildTokenStatusListTokenCache() {
        return Caffeine.newBuilder()
            .maximumSize(cacheProperties.getStatusListCacheSize())
            .expireAfter(buildTokenStatusListExpire(TimeUnit.SECONDS.toNanos(cacheProperties.getStatusListCacheTtl())))
            .build();
    }

    /**
     * 
     * @param maxCacheTTLNs TTL, if smaller than exp or ttl of status list overriding the status list's config
     * @return the caffeine expiry object with correct expiry times configured
     */
        private Expiry<String, Optional<TokenStatusListTokenDto>> buildTokenStatusListExpire(long maxCacheTTLNs) {
            return new Expiry<>() {

                @Override
                public long expireAfterCreate(String key, Optional<TokenStatusListTokenDto> value, long currentTime) {
                    return getTtlOrBackoff(value);
                }

                @Override
                public long expireAfterUpdate(String key, Optional<TokenStatusListTokenDto> value, long currentTime,
                        long currentDuration) {
                    return getTtlOrBackoff(value);
                }

                @Override
                public long expireAfterRead(String key, Optional<TokenStatusListTokenDto> value, long currentTime,
                        long currentDuration) {
                    return currentTime;
                }
                
                private long getTtlOrBackoff(Optional<TokenStatusListTokenDto> value) {
                    return value
                        .map(this::getTTLTime)
                        .orElse(TimeUnit.SECONDS.toNanos(cacheProperties.getRequestBackoffSeconds()));
                }

                private long getTTLTime(TokenStatusListTokenDto value) {
                    long minimumTimeout = TimeUtil.minNanosUntilExpiry(maxCacheTTLNs, TimeUtil.secondsToNanos(value.getExp()));
                    return TimeUtil.minWithNullable(minimumTimeout, TimeUtil.secondsToNanos(value.getTtl()));
                }
            };
        }
}
