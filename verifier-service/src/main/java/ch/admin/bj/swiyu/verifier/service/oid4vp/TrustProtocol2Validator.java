package ch.admin.bj.swiyu.verifier.service.oid4vp;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.JWKSet;

import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.jwtvalidator.UrlRestriction;
import ch.admin.bj.swiyu.tsverifier.TrustStatementVerifier;
import ch.admin.bj.swiyu.tsverifier.statement.TrustMarkers;
import ch.admin.bj.swiyu.tsverifier.statement.TrustVerificationResult;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.trustregistry.TrustStatementCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    @Qualifier("trustStatementValidator")
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
        List<String> statementJwts = statementProvider.getAllIssuanceStatementsFor(issuerDid);
        TrustStatementVerifier tsVerifier = new TrustStatementVerifier(statementJwts, didKidParser);
        TrustVerificationResult result = tsVerifier.verifyIssuanceStatements(trustAnchor.did(), issuerDid, vct);
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
}
