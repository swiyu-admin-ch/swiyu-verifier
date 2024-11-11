package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.BbsKeyProperties;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.statuslist.StatusListReferenceFactory;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresentationFormatFactory {

    private final BbsKeyProperties bbsKeyProperties;
    private final IssuerPublicKeyLoader issuerPublicKeyLoader;
    private final StatusListReferenceFactory statusListReferenceFactory;

    public CredentialVerifier getFormatBuilder(String credentialToBeProcessed,
                                               ManagementEntity managementEntity,
                                               PresentationSubmission presentationSubmission,
                                               VerificationManagementRepository verificationManagementRepository) {

        // TODO assume only 1 submission at the moment
        var format = presentationSubmission.getDescriptorMap().getFirst().getFormat();

        if (format == null || format.isEmpty()) {
            throw new IllegalArgumentException("No format " + format);
        }

        return switch (format) {
            case "ldp_vp", "ldp_vc" ->
                    new LdpCredential(credentialToBeProcessed, managementEntity, presentationSubmission, verificationManagementRepository, statusListReferenceFactory, bbsKeyProperties);
            case SDJWTCredential.CREDENTIAL_FORMAT ->
                    new SDJWTCredential(credentialToBeProcessed, managementEntity, presentationSubmission, verificationManagementRepository, issuerPublicKeyLoader, statusListReferenceFactory);
            default -> throw new IllegalArgumentException("Unknown format: " + format);
        };
    }
}
