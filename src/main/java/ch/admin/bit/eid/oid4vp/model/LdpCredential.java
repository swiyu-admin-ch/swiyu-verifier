package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.eid.bbscryptosuite.BbsCryptoSuite;
import ch.admin.eid.bbscryptosuite.CryptoSuiteVerificationResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LdpCredential extends CredentialVerifier {

    private final BBSKeyConfiguration bbsKeyConfiguration;

    LdpCredential(final String vpToken,
                  final ManagementEntity managementEntity,
                  final PresentationSubmission presentationSubmission,
                  final VerificationManagementRepository verificationManagementRepository,
                  BBSKeyConfiguration bbsKeyConfiguration) {
        super(vpToken, managementEntity, presentationSubmission, verificationManagementRepository);
        this.bbsKeyConfiguration = bbsKeyConfiguration;
    }

    @Override
    public void verifyPresentation() {
        // TODO - Validate the integrity, authenticity, and Holder Binding of any Verifiable Presentation provided in the VP Token according to the rules of the respective Presentation format
        String verifiedDocument;
        try {
            verifiedDocument = verifyProofBBS(vpToken, managementEntity.getRequestNonce());
        } catch (Exception e) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "The credential data integrity signature could not be verified", managementEntity);
        }

        // Confirm that the returned Credential(s) meet all criteria sent in the Presentation Definition in the Authorization Request.
        checkPresentationDefinitionCriteria(vpToken);

        // TODO - Perform the checks required by the Verifier's policy based on the set of trust requirements such as trust frameworks it belongs to (i.e., revocation checks), if applicable.
        managementEntity.setState(VerificationStatusEnum.SUCCESS);
        managementEntity.setWalletResponse(ResponseData.builder().credentialSubjectData(verifiedDocument).build());
        verificationManagementRepository.save(managementEntity);
    }

    private String verifyProofBBS(String bbsCredential, String nonce) throws VerificationException {

        CryptoSuiteVerificationResult verificationResult;

        try (BbsCryptoSuite suite = new BbsCryptoSuite(bbsKeyConfiguration.getBBSKey())) {
            verificationResult = suite.verifyProof(bbsCredential, nonce, bbsKeyConfiguration.getPublicBBSKey());
        }

        if (!verificationResult.component1()) {
            throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, "Verification error", managementEntity);
        }

        return verificationResult.getVerifiedDocument();
    }
}
