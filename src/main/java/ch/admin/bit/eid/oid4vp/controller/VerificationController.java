package ch.admin.bit.eid.oid4vp.controller;

import ch.admin.bit.eid.oid4vp.exception.VerificationException;
import ch.admin.bit.eid.oid4vp.model.PresentationSubmissionDto;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.bit.eid.oid4vp.service.RequestObjectService;
import ch.admin.bit.eid.oid4vp.service.VerificationService;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

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
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> receiveVerificationPresentation(
            @NotEmpty @PathVariable(name="request_id") UUID requestId,
            @RequestParam(name= "presentationSubmissionDto", required = false) PresentationSubmissionDto presentationSubmissionDto,
            @RequestParam(name="vp_token", required = false) String vpToken,
            @RequestParam(name="error", required = false) String walletError,
            @RequestParam(name="error_description", required = false) String walletErrorDescription) {

        var managementObject = verificationManagementRepository.findById(requestId.toString()).orElseThrow(
                () -> VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND));

        if (managementObject.getState() != VerificationStatusEnum.PENDING) {
            throw VerificationException.submissionError(VerificationErrorEnum.VERIFICATION_PROCESS_CLOSED);

        }

        if (isNoneBlank(walletError)) {
            verificationService.processHolderVerificationRejection(managementObject, walletError, walletErrorDescription);
            return null;
        }

        if (isBlank(vpToken) || isNull(presentationSubmissionDto)) {
            throw VerificationException.submissionError(VerificationErrorEnum.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM);
        }
        verificationService.processPresentation(managementObject, vpToken, presentationSubmissionDto);

        return new HashMap<>();

    }
}