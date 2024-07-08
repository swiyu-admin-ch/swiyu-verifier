package ch.admin.bit.eid.oid4vp.controller;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.dto.VerificationPresentationRequest;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.bit.eid.oid4vp.service.RequestObjectService;
import ch.admin.bit.eid.oid4vp.service.VerificationService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ch.admin.bit.eid.oid4vp.model.mapper.PresentationSubmissionMapper.stringToPresentationSubmission;
import static ch.admin.bit.eid.oid4vp.utils.Base64Utils.decodeBase64;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;

/**
 * OpenID4VC Issuance Controller
 * <p>
 * Implements the OpenID4VCI defined endpoints
 * <a href="https://openid.github.io/OpenID4VCI/openid-4-verifiable-credential-issuance-wg-draft.html">OID4VCI Spec</a>
 */
@RestController
@AllArgsConstructor
public class VerificationController {

    private final VerificationManagementRepository verificationManagementRepository;

    private final RequestObjectService requestObjectService;
    private final VerificationService verificationService;


    @GetMapping("/request-object/{request_id}")
    public RequestObject getRequestObject(@PathVariable(name = "request_id") UUID requestId) {
        // TODO Use the signed request object jwt instead of an object
        return requestObjectService.assembleRequestObject(requestId);
    }

    @PostMapping(value = "/request-object/{request_id}/response-data",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    @RequestBody(description = "dummy description", content = @Content(
            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE))
    public Map<String, Object> receiveVerificationPresentation(
            @PathVariable(name="request_id") UUID requestId,
            VerificationPresentationRequest request) {

        ManagementEntity management = verificationManagementRepository.findById(requestId.toString()).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND));

        if (management.getState() != VerificationStatusEnum.PENDING) {
            throw VerificationException.submissionError(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED);
        }

        String walletError = request.getError();
        String walletErrorDescription = request.getError_description();

        if (isNoneBlank(walletError)) {
            verificationService.processHolderVerificationRejection(management, walletError, walletErrorDescription);
            return new HashMap<>();
        }

        PresentationSubmission presentationSubmission = stringToPresentationSubmission(request.getPresentation_submission());

        String vpToken = decodeBase64(request.getVp_token());

        if (isBlank(vpToken) || isNull(presentationSubmission)) {
            throw VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM);
        }

        verificationService.processPresentation(management, vpToken, presentationSubmission);

        return new HashMap<>();
    }
}