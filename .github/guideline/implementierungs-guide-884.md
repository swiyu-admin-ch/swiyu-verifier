# Implementation Guideline: Trust Protocol 2.0 for Generic Verifier

## Context & Objective
The Generic Verifier needs to integrate Trust Protocol 2.0 (TP2). During the OpenID4VP Authorization Request generation, the Verifier must inject Trust Statements into the `verifier_info` array claim of the JWT-Secured Authorization Request.

There are three types of statements involved:
1. **idTS (Identity Trust Statement)**: Fetched from TMS API, cached in-memory.
2. **pvaTS (Protected Verification Authorization Trust Statement)**: Fetched from TMS API, cached in-memory.
3. **vqPS (Verification Query Public Statement)**: Dynamically registered at TMS during verification initialization (DCQL submission) and **persisted in the database** (not in-memory cache).

## ⚠️ Architectural Directives & Lessons Learned
Do **NOT** use Spring's `@Cacheable` or `@CacheEvict` for `idTS` and `pvaTS`, even if initial documentation mentions it. Spring Cache does not support dynamic, per-entry Time-To-Live (TTL) based on JWT claims. Implement the following architecture strictly:

### 1. High-Performance Caching (Caffeine)
Create a `TrustStatementCacheService` utilizing `com.github.ben-manes.caffeine:caffeine` directly.
* **Dynamic Eviction (TTL):** Extract the `exp` (expiration) claim from the fetched JWT payloads (`idTS`, `pvaTS`). The cache TTL must be exactly this `exp` minus a configured `clockSkewBufferSeconds`.
* **Cap TTL:** Apply an optional `maxCacheTtlSeconds` to cap the TTL. `effective TTL = min(exp-based TTL, maxCacheTtlSeconds)`.
* **Negative Caching:** If the TMS API fails or returns empty, cache an `Optional.empty()` with a short TTL (e.g., 30 seconds) to prevent retry storms and thread exhaustion.
* **Use Nimbus:** Use `com.nimbusds.jwt.JWTParser` to parse the JWT and extract the `exp` claim. Do not write custom string-split/base64-decode logic.

### 2. Pre-Inject Signature Validation (DID Resolution)
Create a `TrustStatementValidator` to prevent injecting revoked or manipulated statements.
* **Phase 1 (Pre-Cache):** Validate that the `kid` (DID URL) of the fetched JWT matches an allowed host (Trust Registry API URL).
* **Phase 2 (Pre-Inject):** *Before* injecting cached `idTS` and `pvaTS` into the Authorization Request, dynamically resolve the Trust Registry's DID Document and verify the JWT signature. This ensures immediate detection of key rotations.
* **Invalidation:** If signature validation fails, immediately invalidate the specific DID entry in the cache.

### 3. State Mutation & Thread Safety
When building the JWT-Secured Authorization Request:
* **No Global State Mutation:** Do not mutate shared singletons or base configurations to inject the `verifier_info`.
* **Thread-Local Copies:** Use a builder pattern (e.g., `.toBuilder().build()`) to create a fresh, thread-local copy of the request payload per transaction before injecting the statements.

### 4. Payload Format (`verifier_info`)
The `TrustStatementInjectionService` must load the active `idTS` and `pvaTS` from the Caffeine cache, and the `vqPS` from the database. It then embeds them into the `verifier_info` array.
* **Strict Format:** Each statement must be embedded as: `{"format": "jwt", "data": "<jwt-string>"}`. Do not include `credential_ids` or other claims in these objects.

### 5. Targeted Cache Maintenance ("Blast Radius")
Create a `CacheMaintenanceService` for manual invalidation (e.g., for DID public keys).
* **Evict by Key:** Never use `.clear()` to wipe an entire cache, as it affects all tenants/DIDs. Use `.evict(did)` to target specific entries only.

### 6. Clean Code & Java Best Practices
* **Exceptions:** Never catch generic `Exception e`. Catch specific exceptions (e.g., `WebClientException`, `ParseException`).
* **Magic Numbers:** Extract numbers (like fallback TTLs or array limits) into named `static final` constants.
* **Dependency Injection:** Strictly use Constructor Injection (`@RequiredArgsConstructor`) with `final` fields. No `@Autowired` on fields.
* **Documentation:** All `public` methods and classes must have clear English JavaDoc explaining *why* and *what* they do.

## Configuration Properties Required
Ensure the service reacts to a conditional property (e.g., `@ConditionalOnProperty(prefix = "swiyu.trust-registry", name = "api-url")`) so the system degrades gracefully if TP2 is disabled. Required configuration values include:
- `api-url`
- `customer-key` / `customer-secret` (for Basic Auth via WebClient)
- `max-cache-size`
- `clock-skew-buffer-seconds`
- `max-cache-ttl-seconds`