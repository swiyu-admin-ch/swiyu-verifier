package ch.admin.bj.swiyu.verifier.service.oid4vp;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;

import ch.admin.bj.swiyu.jwtvalidator.DidJwtValidator;
import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.jwtvalidator.JwtValidatorException;
import ch.admin.bj.swiyu.jwtvalidator.UrlRestriction;
import ch.admin.bj.swiyu.statuslist.dto.TokenStatusListTokenDto;
import ch.admin.bj.swiyu.tsverifier.TrustStatementVerifier;
import ch.admin.bj.swiyu.tsverifier.statement.TrustMarkers;
import ch.admin.bj.swiyu.tsverifier.statement.TrustVerificationResult;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import ch.admin.bj.swiyu.verifier.service.publickey.LoadingPublicKeyOfIssuerFailedException;
import ch.admin.bj.swiyu.verifier.service.statuslist.StatusListResolverAdapter;
import ch.admin.bj.swiyu.verifier.service.trustregistry.TrustStatementCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service that validates trust according to the Swiss‑Trust‑Protocol 2.0.
 *
 * <p>
 * The validator obtains all issuance‑type trust statements for a given
 * issuer, resolves the public keys that are required to verify those
 * statements, resolves any status‑list tokens referenced by the statements and
 * finally delegates the actual verification logic to
 * {@link TrustStatementVerifier}.
 *
 * <p>
 * The result of the verification is a {@link TrustVerificationResult}
 * containing the evaluated {@link TrustMarkers}. The public method
 * {@link #isTrusted(String, String, Management)} returns {@code true} when the
 * issuer's trust markers indicate a trusted issuer (i.e. when
 * {@link TrustMarkers#isTrustedIssuer()} evaluates to {@code true}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnBean(TrustStatementCacheService.class)
public class TrustProtocol2Validator {

    private final TrustStatementCacheService statementProvider;
    private final StatusListResolverAdapter statusListResolverAdapter;
    @Qualifier("trustStatementValidator")
    private final DidJwtValidator jwtValidator;
    private final IssuerPublicKeyLoader keyLoader;
    private final ObjectMapper mapper;
    private final DidKidParser didKidParser = new DidKidParser();

    /**
     * Determines whether the given {@code issuerDid} can be considered trusted
     * for issuing the credential type {@code vct} under the supplied
     * {@link Management} configuration.
     *
     * @param issuerDid  the DID of the issuer whose trust is being evaluated
     * @param vct        the Verifiable Credential Type that the issuer wants to
     *                   issue (e.g. {@code "urn:ch.admin.fedpol.eid"})
     * @param management the management configuration that contains the list of
     *                   {@link TrustAnchor}s to be used for verification
     * @return {@code true} if at least one trust anchor yields a
     *         {@link TrustMarkers#isTrustedIssuer()} result of {@code true},
     *         otherwise {@code false}
     */
    public boolean isTrusted(String issuerDid, String vct, Management management) {
        Map<String, TrustVerificationResult> verificationResults = management.getTrustAnchors().stream()
                .collect(Collectors.toMap(TrustAnchor::did,
                        trustAnchor -> evaluateTrust(issuerDid, vct, trustAnchor)));
        // TODO EIDOMNI-867 Save verification result for the business verifier instead
        // of rejecting the VC
        return verificationResults.values().stream().anyMatch(result -> result.markers().isTrustedIssuer());
    }

    /**
     * Executes the full verification flow for a single {@link TrustAnchor}.
     *
     * <ol>
     * <li>Creates a {@link UrlRestriction} that limits the verification to the
     * URL of the supplied {@code trustAnchor}.</li>
     * <li>Fetches all issuance‑type trust statements for {@code issuerDid}.
     * </li>
     * <li>Instantiates a {@link TrustStatementVerifier} with the statements,
     * the URL restriction and a {@link DidKidParser}.
     * </li>
     * <li>Resolves the public keys required by the statements.
     * </li>
     * <li>Resolves, validates and parses any status‑list tokens referenced by
     * the statements.
     * </li>
     * <li>Calls {@link TrustStatementVerifier#verifyIssuanceStatements(String,
     * String, String, JWKSet, List)} which returns a
     * {@link TrustVerificationResult}.
     * </li>
     * </ol>
     *
     * @return the verification result containing {@link TrustMarkers}
     */
    private TrustVerificationResult evaluateTrust(String issuerDid, String vct, TrustAnchor trustAnchor) {
        UrlRestriction urlRestriction = new UrlRestriction(Set.of(trustAnchor.trustRegistryUri()));
        List<String> statementJwts = statementProvider.getAllIssuanceStatementsFor(issuerDid);
        TrustStatementVerifier tsVerifier = new TrustStatementVerifier(statementJwts, urlRestriction, didKidParser);
        JWKSet publicKeys = getTrustPublicKeys(tsVerifier.getRequiredKeyIds());

        List<TokenStatusListTokenDto> statusListTokens = getStatusLists(tsVerifier.getRequiredStatusLists());
        TrustVerificationResult result = tsVerifier.verifyIssuanceStatements(trustAnchor.did(), issuerDid, vct,
                publicKeys, statusListTokens);
        TrustMarkers markers = result.markers();
        log.debug("Validated Trust Marks for {} with result: identity {}, compliant actor {}, vct {} is governed use case {}, governed use case authorization {}",
            issuerDid, markers.identityTrustMarker(), 
            markers.compliantActorTrustMarker(), 
            vct, 
            markers.governedUseCaseTrustMarker(), 
            markers.governedUseCaseAuthorizationTrustMarker()
        );
        return result;
    }

    /**
     * Resolves each status‑list URI to a JWT, validates the JWT and parses it
     * into a {@link TokenStatusListTokenDto}. Invalid or unparsable tokens are
     * filtered out.
     */
    private List<TokenStatusListTokenDto> getStatusLists(Set<String> statusListsUris) {

        List<String> statusListJwts = Flux.fromIterable(statusListsUris)
            .flatMap(uri -> Mono.fromCallable(() -> statusListResolverAdapter.resolveStatusList(uri))
            .subscribeOn(Schedulers.boundedElastic()))
            .collectList()
            .block();
        List<TokenStatusListTokenDto> statusLists = statusListJwts.stream().map(this::validateTokenStatusListToken).filter(Objects::nonNull).toList();
        log.trace("Fetched {} valid status lists", statusLists.size());
        return statusLists;
    }

    /**
     * Loads the public JWKs for the given set of key IDs. Missing or
     * unparsable keys are logged and omitted from the resulting {@link JWKSet}.
     */
    private JWKSet getTrustPublicKeys(Set<String> requiredKeyIds) {
        List<JWK> jwks = requiredKeyIds.stream().map(keyid -> {
            var issuerDid = didKidParser.getDidFromAbsoluteKid(keyid);
            try {
                return keyLoader.loadJWK(issuerDid, keyid);
            } catch (LoadingPublicKeyOfIssuerFailedException e) {
                log.warn("Trust Protocol 2.0: Failed to load public key for trust statements. Skipping the key", e);
                return null;
            }
        })
                .filter(Objects::nonNull)
                .toList();
        return new JWKSet(jwks);
    }

    /**
     * Validates a status‑list JWT using the {@link DidJwtValidator} and returns
     * the deserialized {@link TokenStatusListTokenDto}. If validation fails the
     * method returns {@code null} and a warning is logged.
     */
    private TokenStatusListTokenDto validateTokenStatusListToken(String tokenStatusListTokenJwt) {
        if (tokenStatusListTokenJwt == null || tokenStatusListTokenJwt.isEmpty()) {
            return null;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(tokenStatusListTokenJwt);
            String kid = jwt.getHeader().getKeyID();
            JWKSet statusListJwkSet = getTrustPublicKeys(Set.of(kid));
            jwtValidator.validateJwt(tokenStatusListTokenJwt, statusListJwkSet);
            return mapper.readValue(jwt.getPayload().toString(), TokenStatusListTokenDto.class);
        } catch (ParseException | JsonProcessingException | JwtValidatorException e) {
            log.warn("Trust Protocol 2.0: Skipping failed to validate token status list {} ", tokenStatusListTokenJwt,
                    e);
        }
        return null;
    }
}
