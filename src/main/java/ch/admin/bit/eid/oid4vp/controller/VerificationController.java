package ch.admin.bit.eid.oid4vp.controller;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.bit.eid.oid4vp.service.RequestObjectService;
import ch.admin.bit.eid.oid4vp.service.VerificationService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

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

    @PostMapping("/request-object/{request_id}/response-data")
    public void receiveVerificationPresentation(
            @PathVariable(name="request_id") UUID requestId,
            @RequestParam(name="presentation_submission", required = false) String presentationSubmission,
            @RequestParam(name="vp_token", required = false) String vpToken,
            @RequestParam(name="error", required = false) String walletError,
            @RequestParam(name="error_description", required = false) String walletErrorDescription) {

        var managementObject = verificationManagementRepository.findById(requestId.toString()).orElseThrow();

        if (managementObject.getState() != VerificationStatusEnum.PENDING) {
            // TODO Raise Exception

        }

        if (walletError != null && !walletError.isEmpty()) {
            verificationService.processErrorResponse(managementObject, walletError, walletErrorDescription);
            return;
        }

        if (vpToken == null || vpToken.isEmpty() || presentationSubmission == null || presentationSubmission.isEmpty()) {
            // TODO Raise Exception
        }
        verificationService.processPresentation(managementObject, vpToken, presentationSubmission);


    }
}