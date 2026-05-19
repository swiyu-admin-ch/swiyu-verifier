package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.ISSUER_NOT_ACCEPTED;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

/**
 * Encapsulates issuer trust validation logic, including accepted issuer lists
 * and trust-anchor / trust-statement based trust.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssuerTrustValidator {

    private final TrustProtocol1Validator trustProtocol1Validator;
    private final TrustProtocol2Validator trustProtocol2Validator;

    /**
     * Validates whether the given issuer is trusted according to the provided management configuration.
     * <p>
     * Trust is established if:
     * <ul>
     *   <li>Both accepted issuer DIDs and trust anchors are empty (all issuers allowed), or</li>
     *   <li>The issuer DID is in the list of accepted issuer DIDs, or</li>
     *   <li>The issuer is directly or indirectly trusted via a trust anchor and a valid trust statement.</li>
     * </ul>
     * If none of these conditions are met, a {@link VerificationException} is thrown.
     *
     * @param issuerDid the DID of the issuer to validate
     * @param vct the credential type (vct) to check trust for
     * @param management the management configuration containing accepted issuers and trust anchors
     * @throws VerificationException if the issuer is not trusted
     */
    public void validateTrust(String issuerDid, String vct, Management management) {
        if (isAcceptedIsser(issuerDid, management)) {
            return;
        }
        var trustAnchors = management.getTrustAnchors();
        boolean trustAnchorsEmpty = trustAnchors == null || trustAnchors.isEmpty();
        if (!trustAnchorsEmpty) {
            if (issuerDid.startsWith("did:tdw")) {
                // Trust Protocl 1.0
                if (trustProtocol1Validator.hasMatchingTrustProtocol1Statement(issuerDid, vct, trustAnchors, management)) {
                    return; // We have a valid trust statement for the vct!
                }
            }
            if (issuerDid.startsWith("did:webvh")) {
                // Trust Protocol 2.0
                if(trustProtocol2Validator.isTrusted(issuerDid, vct, management)) {

                }
                
            }
        }

        throw credentialError(ISSUER_NOT_ACCEPTED, "Issuer not in list of accepted issuers or connected to trust anchor");
    }

    /**
     * Evaluates if the issuer is explicitly trusted
     * @param issuerDid DID of the credential issuer
     * @param management Verification management object
     * @return true if the issuer is explicitly trusted
     */
    private boolean isAcceptedIsser(String issuerDid, Management management) {
        var acceptedIssuerDids = management.getAcceptedIssuerDids();
        var acceptedIssuersEmpty = acceptedIssuerDids == null || acceptedIssuerDids.isEmpty();
        return !acceptedIssuersEmpty && acceptedIssuerDids.contains(issuerDid);
    }

}
