package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.config.BBSKeyConfiguration;
import ch.admin.bit.eid.oid4vp.config.SDJWTConfiguration;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresentationFormatFactory {

    private final BBSKeyConfiguration bbsKeyConfiguration;
    private final SDJWTConfiguration sdjwtConfiguration;

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
            case "ldp_vp" ->
                    new LdpCredential(credentialToBeProcessed, managementEntity, presentationSubmission, verificationManagementRepository, bbsKeyConfiguration);
            case "jwt_vp_json", "jwt_vc" ->
                    new SDJWTCredential(credentialToBeProcessed, managementEntity, presentationSubmission, verificationManagementRepository, sdjwtConfiguration);
            default -> throw new IllegalArgumentException("Unknown format: " + format);
        };
    }
}
