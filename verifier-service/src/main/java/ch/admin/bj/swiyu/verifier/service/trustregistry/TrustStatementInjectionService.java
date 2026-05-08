package ch.admin.bj.swiyu.verifier.service.trustregistry;

import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.dto.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.dto.requestobject.VerifierInfoEntryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects Trust Protocol 2.0 trust statements into the JWT-Secured Authorization Request.
 * <p>
 * Assembles the {@code verifier_info} array by combining:
 * <ul>
 *   <li>{@code idTS} – Identity Trust Statement (fetched from TMS and cached in-memory)</li>
 *   <li>{@code pvaTS} – Protected Verification Authorization Trust Statement (cached in-memory)</li>
 *   <li>{@code vqPS} – Verification Query Public Statement (persisted per session in the database)</li>
 * </ul>
 * Thread safety is guaranteed by the builder pattern: a fresh {@link RequestObjectDto} is created
 * per call via {@code toBuilder().build()} – no shared mutable state is mutated.
 * <p>
 * Conditionally active: if {@code swiyu.trust-registry.api-url} is absent, the Authorization
 * Request is returned unmodified and no trust statements are injected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "swiyu.trust-registry", name = "api-url")
public class TrustStatementInjectionService {

    private final TrustStatementCacheService cacheService;

    /**
     * Returns a new {@link RequestObjectDto} with the {@code verifier_info} array populated from
     * the active {@code idTS}, {@code pvaTS} and the session-specific {@code vqPS}.
     * <p>
     * Unavailable statements are skipped with a warning; the request object is always returned
     * to ensure graceful degradation.
     *
     * @param requestObject    the base request object to enrich
     * @param managementEntity the current verification session carrying the persisted {@code vqPS}
     * @return a new {@link RequestObjectDto} instance with the {@code verifier_info} array set
     */
    public RequestObjectDto injectVerifierInfo(RequestObjectDto requestObject, Management managementEntity) {
        List<VerifierInfoEntryDto> verifierInfo = new ArrayList<>();

        addNullable(verifierInfo, cacheService.getIdentityTrustStatement(), "idTS");
        addNullable(verifierInfo, cacheService.getProtectedVerificationAuthorizationTrustStatement(), "pvaTS");
        addVqPs(verifierInfo, managementEntity);

        if (verifierInfo.isEmpty()) {
            log.warn("No TP2.0 trust statements available – returning request object without verifier_info");
            return requestObject;
        }

        return requestObject.toBuilder()
                .verifierInfo(verifierInfo)
                .build();
    }

    private void addNullable(List<VerifierInfoEntryDto> target, String jwt, String statementType) {
        if (jwt != null) {
            target.add(VerifierInfoEntryDto.ofJwt(jwt));
        } else {
            log.warn("TP2.0: {} not available, skipping injection", statementType);
        }
    }

    private void addVqPs(List<VerifierInfoEntryDto> target, Management managementEntity) {
        String vqPs = managementEntity.getVqPs();
        if (vqPs != null && !vqPs.isBlank()) {
            target.add(VerifierInfoEntryDto.ofJwt(vqPs));
        } else {
            log.warn("TP2.0: vqPS not persisted for managementId={}, skipping injection",
                    managementEntity.getId());
        }
    }
}
