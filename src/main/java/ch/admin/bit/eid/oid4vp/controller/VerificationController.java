package ch.admin.bit.eid.oid4vp.controller;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.dto.VerificationPresentationRequestDto;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.bit.eid.oid4vp.service.RequestObjectService;
import ch.admin.bit.eid.oid4vp.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "Get Request Object", description = "Can return a RequestObject as JSON Object or a SignedJwt String depending of JAR (JWT secured authorization request) flag in verifier management")
    public Object getRequestObject(@PathVariable(name = "request_id") UUID requestId) {
        return requestObjectService.assembleRequestObject(requestId);
    }

    @PostMapping(value = "/request-object/{request_id}/response-data",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseStatus(HttpStatus.OK)
    @RequestBody(description = "", content = @Content(mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE))
    public void receiveVerificationPresentation(
            @PathVariable(name = "request_id") UUID requestId,
            VerificationPresentationRequestDto request) {

        ManagementEntity managementEntity = verificationManagementRepository.findById(requestId).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND, null));

        if (managementEntity.getState() != VerificationStatusEnum.PENDING) {
            throw VerificationException.submissionError(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED, null);
        }

        if (isNoneBlank(request.getError())) {
            verificationService.processHolderVerificationRejection(managementEntity, request.getError_description());
            return;
        }

        PresentationSubmission presentationSubmission = parsePresentationSubmission(request.getPresentation_submission(), managementEntity);

        if (isBlank(request.getVp_token()) || isNull(presentationSubmission)) {
            throw VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM, managementEntity);
        }

        verificationService.processPresentation(managementEntity, request.getVp_token(), presentationSubmission);
    }

    private PresentationSubmission parsePresentationSubmission(String presentationSubmissionStr, ManagementEntity management) {
        try {
            return stringToPresentationSubmission(presentationSubmissionStr);
        } catch (IllegalArgumentException e) {
            throw VerificationException.submissionError(VerificationErrorEnum.INVALID_REQUEST, management,
                    e.getMessage());
        }
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
