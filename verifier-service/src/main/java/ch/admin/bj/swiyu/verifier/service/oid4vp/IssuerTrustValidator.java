package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.TrustRegistryProperties;
import ch.admin.bj.swiyu.verifier.common.exception.ConfigurationException;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.IssuerTrustMarker;
import ch.admin.bj.swiyu.verifier.domain.TrustMethod;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;


import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.ISSUER_NOT_ACCEPTED;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.UNSUPPORTED_FORMAT;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

import java.util.Optional;

/**
 * Encapsulates issuer trust validation logic, including accepted issuer lists
 * and trust-anchor / trust-statement based trust.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssuerTrustValidator {

    private final Optional<TrustProtocol2Validator> trustProtocol2Validator;
    private final TrustRegistryProperties trustRegistryProperties;

    /**
     * Validates whether the given issuer is trusted according to the provided management configuration.
     * <p>
     * Trust is established if:
     * <ul>
     *   <li>The issuer DID is in the list of accepted issuer DIDs, or</li>
     *   <li>The issuer is trusted through trust statements signed by the trust anchor.</li>
     * </ul>
     * If none of these conditions are met, a {@link VerificationException} is thrown.
     *
     * @param issuerDid the DID of the issuer to validate
     * @param vct the credential type (vct) to check trust for
     * @param management the management configuration containing accepted issuers and trust anchors
     * @return {@link IssuerTrustMarker} computed from either accepted authorities provided in management or through the trust protocol
     * @throws VerificationException if the issuer is not trusted
     */
    public IssuerTrustMarker validateTrust(String issuerDid, String vct, Management management) {
        if (isAcceptedIssuer(issuerDid, management)) {
            return IssuerTrustMarker.builder().isTrusted(true).trustMethod(TrustMethod.TRUSTED_AUTHORITY).build();
        }
        if (hasTrustProtocolAnchor()) {
            return evaluateTrustProtocol(issuerDid, vct);
        }
        
        throw credentialError(ISSUER_NOT_ACCEPTED, "Issuer not in list of accepted issuers or connected to trust anchor");
    }


    /**
     * Validates if Trust in the Issuer for the given vct can be established using the Trust Protocol.
     * 
     * @return {@code true} If one of the configured trust anchors provided trust establishing statements about the issuer.
     *         {@code false} If no trust anchors are defined, no trust protocol for the did method is supported or no valid trust statements have been found.
     */
    private IssuerTrustMarker evaluateTrustProtocol(String issuerDid, String vct) {

        if (issuerDid.startsWith("did:webvh")) {
            log.trace("Validate Trust with Trust Protocol 2.0 for issuer {} with vct {}", issuerDid, vct);
            // Trust Protocol 2.0
            return trustProtocol2Validator
                .map(sv -> sv.isTrusted(issuerDid, vct))
                .orElseThrow(() -> new ConfigurationException("No Trust Registry is configured while trust anchors are defined"));
        }
        throw credentialError(UNSUPPORTED_FORMAT, String.format("DID Format %s is not supported", issuerDid));
    }

    private boolean hasTrustProtocolAnchor() {
        return StringUtils.isNotEmpty(trustRegistryProperties.getTrustIssuerDid());
    }


    /**
     * Evaluates if the issuer is explicitly trusted
     * @param issuerDid DID of the credential issuer
     * @param management Verification management object
     * @return true if the issuer is explicitly trusted
     */
    private boolean isAcceptedIssuer(String issuerDid, Management management) {
        var acceptedIssuerDids = management.getAcceptedIssuerDids();
        var acceptedIssuersEmpty = acceptedIssuerDids == null || acceptedIssuerDids.isEmpty();
        return !acceptedIssuersEmpty && acceptedIssuerDids.contains(issuerDid);
    }

}
