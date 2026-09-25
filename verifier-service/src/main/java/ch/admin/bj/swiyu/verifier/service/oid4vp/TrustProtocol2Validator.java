package ch.admin.bj.swiyu.verifier.service.oid4vp;

import java.util.List;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.JWKSet;

import ch.admin.bj.swiyu.jwtvalidator.DidKidParser;
import ch.admin.bj.swiyu.jwtvalidator.UrlRestriction;
import ch.admin.bj.swiyu.tsverifier.TrustStatementVerifier;
import ch.admin.bj.swiyu.tsverifier.statement.TrustMarkers;
import ch.admin.bj.swiyu.tsverifier.statement.TrustVerificationResult;
import ch.admin.bj.swiyu.verifier.domain.IssuerTrustMarker;
import ch.admin.bj.swiyu.verifier.domain.TrustMethod;
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
    private final TrustRegistryProperties trustRegistryProperties;

    /**
     * Determines whether the given {@code issuerDid} can be considered trusted
     * for issuing the credential type {@code vct} under the supplied
     * {@link Management} configuration.
     *
     * @param issuerDid  the DID of the issuer whose trust is being evaluated
     * @param vct        the Verifiable Credential Type that the issuer wants to
     *                   issue (e.g. {@code "urn:ch.admin.fedpol.eid"})
     * @return {@code true} if at least one trust anchor yields a
     *         {@link TrustMarkers#isTrustedIssuer()} result of {@code true},
     *         otherwise {@code false}
     */
    public IssuerTrustMarker isTrusted(String issuerDid, String vct) {
        log.debug("Evaluating Trust for Anchor {}", trustRegistryProperties.getTrustIssuerDid());
        TrustVerificationResult verificationResult = evaluateTrust(issuerDid, vct);
        TrustMarkers markers = verificationResult.markers();
        return IssuerTrustMarker.builder()
            .trustMethod(TrustMethod.TRUST_PROTOCOL_2_0)
            .isTrusted(verificationResult.markers().isTrustedIssuer())
            .identityTrustMarker(markers.identityTrustMarker())
            .compliantActorTrustMarker(markers.compliantActorTrustMarker())
            .governedUseCaseTrustMarker(markers.governedUseCaseTrustMarker())
            .governedUseCaseAuthorizationTrustMarker(markers.governedUseCaseAuthorizationTrustMarker())
            .build();
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
    private TrustVerificationResult evaluateTrust(String issuerDid, String vct) {
        List<String> statementJwts = statementProvider.getAllIssuanceStatementsFor(issuerDid);
        TrustStatementVerifier tsVerifier = new TrustStatementVerifier(statementJwts, didKidParser);
        TrustVerificationResult result = tsVerifier.verifyIssuanceStatements(trustRegistryProperties.getTrustIssuerDid(), issuerDid, vct);
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
