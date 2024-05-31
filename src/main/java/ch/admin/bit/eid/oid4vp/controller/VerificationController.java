package ch.admin.bit.eid.oid4vp.controller;

import ch.admin.bit.eid.oid4vp.config.ApplicationConfiguration;
import ch.admin.bit.eid.oid4vp.model.dto.RequestObject;
import ch.admin.bit.eid.oid4vp.repository.VerificationManagementRepository;
import ch.admin.bit.eid.oid4vp.service.RequestObjectService;
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
    private static final String OID4VCI_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:pre-authorized_code";

    private final ApplicationConfiguration applicationConfiguration;

    private final VerificationManagementRepository presentationDefinitionRepository;

    private final RequestObjectService requestObjectService;


    /**
     * Endpoint to fetch the Request Object of a Verification Request.
     *
     * @param request_id id of the request object to be returnd
     * @return the request object
     */
    @GetMapping("/request-object/{request_id}")
    public RequestObject getRequestObject(@PathVariable UUID request_id) throws IOException {
        // TODO Use the signed request object jwt instead of an object
        return requestObjectService.assembleRequestObject(request_id);
    }

    @PostMapping("/request-object/{request_id}/response-data")
    public HashMap<String, Object> receiveVerifcationPresentation(
            UUID request_id,
            @RequestParam(name="presentation_submission", required = false) String presentationSubmission,
            @RequestParam(name="vp_token", required = false) String vpToken,
            @RequestParam(name="error", required = false) String walletError,
            @RequestParam(name="error_description", required = false) String walletErrorDescription) {

        return new HashMap<>();
    }
}