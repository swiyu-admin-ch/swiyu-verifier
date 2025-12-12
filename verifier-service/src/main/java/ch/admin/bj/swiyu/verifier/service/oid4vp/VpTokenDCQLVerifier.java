package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.DcqlCredential;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

@Service
@Slf4j
public class VpTokenDCQLVerifier extends VpTokenVerifier {

    public VpTokenDCQLVerifier(IssuerPublicKeyLoader issuerPublicKeyLoader,
                               StatusListReferenceFactory statusListReferenceFactory,
                               ApplicationProperties applicationProperties,
                               VerificationProperties verificationProperties) {
        super(issuerPublicKeyLoader, statusListReferenceFactory, applicationProperties, verificationProperties);
    }

    @Override
    protected SdJwt verifyVpTokenTrustStatement(SdJwt vpToken, Management management) {
        // For DCQL verification, trust statement verification is not used directly in this class.
        throw new UnsupportedOperationException("Trust-statement verification is handled by VpTokenTrustStatementVerifier");
    }

    public SdJwt verifyVpTokenForDCQLRequest(SdJwt vpToken, Management management, DcqlCredential dcqlCredential) {
        // Validate Basic JWT
        verifyVerifiableCredentialJWT(vpToken, management);

        // If Key Binding is present, validate that it is correct
        var requireKeyBinding = Boolean.TRUE.equals(dcqlCredential.getRequireCryptographicHolderBinding());

        if (vpToken.hasKeyBinding()) {
            validateKeyBinding(vpToken, management);
        } else if (requireKeyBinding || requiresKeyBinding(vpToken.getClaims())) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }
        verifyStatus(vpToken.getClaims().getClaims(), management);

        // Resolve Disclosures
        validateDisclosures(vpToken, management);

        return vpToken;
    }
}
