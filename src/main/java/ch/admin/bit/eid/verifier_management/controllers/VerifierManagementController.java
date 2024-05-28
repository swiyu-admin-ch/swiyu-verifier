package ch.admin.bit.eid.verifier_management.controllers;

import ch.admin.bit.eid.verifier_management.models.AuthorizationResponseData;
import ch.admin.bit.eid.verifier_management.models.dto.GetVerificationResponseDto;
import ch.admin.bit.eid.verifier_management.models.dto.PresentationDefinitionRequestDto;
import ch.admin.bit.eid.verifier_management.models.entities.VerificationManagement;
import ch.admin.bit.eid.verifier_management.services.AuthorizationResponseDataService;
import ch.admin.bit.eid.verifier_management.services.PresentationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
public class VerifierManagementController {

    private final PresentationService presentationService;
    private final AuthorizationResponseDataService authDataService;

    @PostMapping("/verifications")
    @Operation(summary = "Creates a new verification process with the given attributes")
    public VerificationManagement createVerification(@RequestBody PresentationDefinitionRequestDto requestDto) {

        return this.presentationService.createVerificationManagement(requestDto);
    }

    @GetMapping("/verifications/{verificationId}")
    @Operation(summary = "Returns the state of the verification & if applicable the data that was sent from the holder")
    public GetVerificationResponseDto getVerification(@PathVariable UUID verificationId) {

        VerificationManagement verificationManagement = this.presentationService.getVerification(verificationId);

        AuthorizationResponseData authorizationResponseData = authDataService.getAuthorizationResponseData(verificationManagement.getAuthorizationRequestId());

        return GetVerificationResponseDto.builder()
                .id(verificationManagement.getId())
                .authorizationRequestObjectUri(verificationManagement.getAuthorizationRequestObjectUri())
                .authorizationRequestId(verificationManagement.getAuthorizationRequestId())
                .status(verificationManagement.getStatus())
                .authorizationResponseData(authorizationResponseData)
                .build();
    }
}
