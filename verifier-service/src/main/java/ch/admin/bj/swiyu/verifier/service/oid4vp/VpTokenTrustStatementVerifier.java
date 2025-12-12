package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.config.VerificationProperties;
import ch.admin.bj.swiyu.verifier.domain.SdJwt;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.statuslist.StatusListReferenceFactory;
import ch.admin.bj.swiyu.verifier.service.publickey.IssuerPublicKeyLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode.HOLDER_BINDING_MISMATCH;
import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.credentialError;

@Service
@Slf4j
public class VpTokenTrustStatementVerifier extends VpTokenVerifier {

    public VpTokenTrustStatementVerifier(IssuerPublicKeyLoader issuerPublicKeyLoader,
                                         StatusListReferenceFactory statusListReferenceFactory,
                                         ApplicationProperties applicationProperties,
                                         VerificationProperties verificationProperties) {
        super(issuerPublicKeyLoader, statusListReferenceFactory, applicationProperties, verificationProperties);
    }

    @Override
    public SdJwt verifyVpTokenTrustStatement(SdJwt vpToken, Management management) {
        verifyVerifiableCredentialJWT(vpToken, management);

        if (vpToken.hasKeyBinding()) {
            validateKeyBinding(vpToken, management);
        } else if (requiresKeyBinding(vpToken.getClaims())) {
            throw credentialError(HOLDER_BINDING_MISMATCH, "Missing Holder Key Binding Proof");
        }

        verifyStatus(vpToken.getClaims().getClaims(), management);
        validateDisclosures(vpToken, management);

        return vpToken;
    }
}
