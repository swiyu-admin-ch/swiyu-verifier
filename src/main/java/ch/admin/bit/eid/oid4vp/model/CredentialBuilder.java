package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.FormatAlgorithm;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.nonNull;

@Getter
@Slf4j
public abstract class CredentialBuilder {

    protected ManagementEntity managementEntity;
    protected String vpToken;
    protected PresentationSubmission presentationSubmission;

    public CredentialBuilder credentialOffer(final String vpToken,
                                             final ManagementEntity managementEntity,
                                             final PresentationSubmission presentationSubmission) {
        this.managementEntity = managementEntity;
        this.vpToken = vpToken;
        this.presentationSubmission = presentationSubmission;

        return this;
    }

    public abstract ManagementEntity verifyPresentation();

    public ManagementEntity verify() {
        return verifyPresentation();
    }

    protected Set<String> getRequestedFormats(ManagementEntity management) {

        PresentationDefinition presentationDefinition = management.getRequestedPresentation();
        Map<String, FormatAlgorithm> formats = new HashMap<>();

        addFormatsToMap(presentationDefinition.getFormat(), formats);
        presentationDefinition.getInputDescriptors().forEach(descriptor -> addFormatsToMap(descriptor.getFormat(), formats));

        return formats.keySet();
    }

    protected void updateManagementObjectAndThrowVerificationError(String errorMessage, ManagementEntity management) throws VerificationException {

        management.setWalletResponse(ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
        management.setState(VerificationStatusEnum.FAILED);
        // TODO verificationManagementRepository.save(management);

        throw VerificationException.credentialError(ResponseErrorCodeEnum.CREDENTIAL_INVALID, errorMessage);
    }

    private void addFormatsToMap(Map<String, FormatAlgorithm> inputFormats, Map<String, FormatAlgorithm> outputFormats) {
        if (nonNull(inputFormats) && !inputFormats.isEmpty()) {
            outputFormats.putAll(inputFormats);
        }
    }
}
