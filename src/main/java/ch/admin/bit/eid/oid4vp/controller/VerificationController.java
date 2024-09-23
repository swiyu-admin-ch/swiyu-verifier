package ch.admin.bit.eid.oid4vp.controller;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.dto.VerificationPresentationRequest;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.bit.eid.oid4vp.service.RequestObjectService;
import ch.admin.bit.eid.oid4vp.service.VerificationService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static ch.admin.bit.eid.oid4vp.model.mapper.PresentationSubmissionMapper.stringToPresentationSubmission;
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
@Slf4j
public class VerificationController {

    private final VerificationManagementRepository verificationManagementRepository;

    private final RequestObjectService requestObjectService;
    private final VerificationService verificationService;


    @GetMapping("/request-object/{request_id}")
    public RequestObject getRequestObject(@PathVariable(name = "request_id") UUID requestId) {
        // TODO EID-1777 Use the signed request object jwt instead of an object
        return requestObjectService.assembleRequestObject(requestId);
    }

    @PostMapping(value = "/request-object/{request_id}/response-data",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.OK)
    @RequestBody(description = ""
            , content = @Content(
            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE))
    public void receiveVerificationPresentation(
            @PathVariable(name = "request_id") UUID requestId,
            VerificationPresentationRequest request) {

        ManagementEntity managementEntity = verificationManagementRepository.findById(requestId).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND, null));

        if (managementEntity.getState() != VerificationStatusEnum.PENDING) {
            throw VerificationException.submissionError(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED, null);
        }

        if (isNoneBlank(request.getError())) {
            verificationService.processHolderVerificationRejection(managementEntity, request.getError_description());
            return;
        }

        PresentationSubmission presentationSubmission = stringToPresentationSubmission(request.getPresentation_submission());

        if (isBlank(request.getVp_token()) || isNull(presentationSubmission)) {
            throw VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM, managementEntity);
        }

        verificationService.processPresentation(managementEntity, request.getVp_token(), presentationSubmission);
    }


    @ExceptionHandler(VerificationException.class)
    protected ResponseEntity<Object> handleVerificationException(VerificationException e) {
        HttpStatus responseStatus = HttpStatus.BAD_REQUEST;
        if (e.getError().getError().equals(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND)) {
            responseStatus = HttpStatus.NOT_FOUND;
        }

        ManagementEntity managementEntity = e.getManagementEntity();

        if (managementEntity != null) {
            managementEntity.setState(VerificationStatusEnum.FAILED);
            managementEntity.setWalletResponse(ResponseData.builder().errorCode(e.getError().getErrorCode()).build());
            verificationService.updateManagement(e.getManagementEntity());
        }
        var error = e.getError();
        log.warn(String.format("The received verification presentation could not be verified - caused by %s - %s", error.getError(), error.getErrorDescription()), e);
        return new ResponseEntity<>(error, responseStatus);
    }
}
