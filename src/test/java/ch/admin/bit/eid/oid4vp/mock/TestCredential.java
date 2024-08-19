package ch.admin.bit.eid.oid4vp.mock;

import ch.admin.bit.eid.oid4vp.model.CredentialVerifier;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;

public class TestCredential extends CredentialVerifier {
    public TestCredential(
            final String vpToken,
            final ManagementEntity managementEntity,
            final PresentationSubmission presentationSubmission,
            final VerificationManagementRepository verificationManagementRepository
    ) {
        super(vpToken, managementEntity, presentationSubmission, verificationManagementRepository);
    }

    @Override
    public ManagementEntity verifyPresentation() {
        return null;
    }
}
