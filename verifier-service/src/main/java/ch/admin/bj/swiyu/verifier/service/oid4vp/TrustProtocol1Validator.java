package ch.admin.bj.swiyu.verifier.service.oid4vp;

import java.text.ParseException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.TrustAnchor;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Deprecated(since = "Trust Protocol 2.0")
public class TrustProtocol1Validator {
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final SdJwtVpTokenVerifier sdJwtVpTokenVerifier;

    /**
     * Evaluate the trust protocol 1.0 trust
     */
    public boolean hasMatchingTrustProtocol1Statement(String issuerDid, String vct, List<TrustAnchor> trustAnchors, Management management) {
        // Direct trust: issuer DID matches a trust anchor DID
        return isDirectlyTrustedIssuer(issuerDid, trustAnchors)
                || isTrustedViaRegistry(issuerDid, vct, trustAnchors, management);
    }

    private boolean isDirectlyTrustedIssuer(String issuerDid, List<TrustAnchor> trustAnchors) {
        return trustAnchors.stream().anyMatch(trustAnchor -> trustAnchor.did().equals(issuerDid));
    }

    private boolean isTrustedViaRegistry(String issuerDid, String vct, List<TrustAnchor> trustAnchors, Management management) {
        for (var trustAnchor : trustAnchors) {
            List<String> trustStatements = fetchTrustStatementIssuance(vct, trustAnchor);
            if (trustStatements.isEmpty()) {
                log.debug("Failed to fetch trust statements for vct {} from {}", vct, trustAnchor.trustRegistryUri());
                continue;
            }

            if (verifyTrustStatements(issuerDid, vct, trustAnchor, trustStatements, management)) {
                return true;
            }
        }
        return false;
    }

    private boolean verifyTrustStatements(String issuerDid, String vct, TrustAnchor trustAnchor,
                                          List<String> trustStatements, Management management) {
        for (var rawTrustStatement : trustStatements) {
            if (validateTrustStatement(issuerDid, vct, trustAnchor, rawTrustStatement, management)) {
                return true;
            }
        }
        return false;
    }

    private boolean validateTrustStatement(String issuerDid, String vct, TrustAnchor trustAnchor,
                                           String rawTrustStatement, Management management) {
        try {
            return isProvidingTrust(issuerDid, vct, trustAnchor, rawTrustStatement, management);
        } catch (VerificationException e) {
            log.debug("Failed to verify trust statement for vct {} from {} - {}: {}",
                    vct, trustAnchor.trustRegistryUri(), e.getErrorResponseCode(), e.getErrorDescription());
            return false;
        } catch (ParseException e) {
            log.info("Trust statement is malformed - missing canIssue claim");
            return false;
        }
    }

    private boolean isProvidingTrust(String issuerDid, String vct, TrustAnchor trustAnchor,
                                     String rawTrustStatement, Management management) throws ParseException {
        var trustStatement = new SdJwt(rawTrustStatement);
        trustStatement = sdJwtVpTokenVerifier.verifyVpTokenTrustStatement(trustStatement, management);
        return issuerDid.equals(trustStatement.getClaims().getSubject())
                && trustAnchor.did().equals(trustStatement.getClaims().getIssuer())
                && vct.equals(trustStatement.getClaims().getStringClaim("canIssue"));
    }

    private List<String> fetchTrustStatementIssuance(String vct, TrustAnchor trustAnchor) {
        if (StringUtils.isBlank(vct)) {
            return List.of();
        }
        try {
            return issuerPublicKeyLoader.loadTrustStatement(trustAnchor.trustRegistryUri(), vct);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

}
