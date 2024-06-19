package ch.admin.bit.eid.oid4vp.controller;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.PresentationSubmission;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.bit.eid.oid4vp.service.RequestObjectService;
import ch.admin.bit.eid.oid4vp.service.VerificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    private final ObjectMapper objectMapper;


    /**
     * Endpoint to fetch the Request Object of a Verification Request.
     *
     * @param requestId id of the request object to be returnd
     * @return the request object
     */
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
            @Valid @RequestParam(name= "presentation_submission", required = false) String presentationSubmissionString,
            @Valid @RequestParam(name="vp_token", required = false) String vpToken,
            @RequestParam(name="error", required = false) String walletError,
            @RequestParam(name="error_description", required = false) String walletErrorDescription) {

        PresentationSubmission presentationSubmission = getPresentationSubmissionDto(presentationSubmissionString);

        ManagementEntity management = verificationManagementRepository.findById(requestId.toString()).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND));

        if (management.getState() != VerificationStatusEnum.PENDING) {
            throw VerificationException.submissionError(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED);
        }

        if (isNoneBlank(walletError)) {
            verificationService.processHolderVerificationRejection(management, walletError, walletErrorDescription);
            return null;
        }

        if (isBlank(vpToken) || isNull(presentationSubmission)) {
            throw VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM);
        }

        verificationService.processPresentation(management, vpToken, presentationSubmission);

        // TODO check if this is correct
        return new HashMap<>();
    }

    private PresentationSubmission getPresentationSubmissionDto(String presentationSubmissionString) {
        PresentationSubmission presentationSubmission;

        if (isNull(presentationSubmissionString)) {
            return null;
        }

        try {
            presentationSubmission = objectMapper.readValue(presentationSubmissionString, PresentationSubmission.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        return presentationSubmission;
    }
}