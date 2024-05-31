package ch.admin.bit.eid.verifier_management.controllers;

import ch.admin.bit.eid.verifier_management.models.Management;
import ch.admin.bit.eid.verifier_management.models.dto.CreateManagementRequestDto;
import ch.admin.bit.eid.verifier_management.models.dto.CreateManagementResponseDto;
import ch.admin.bit.eid.verifier_management.services.ManagementService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static ch.admin.bit.eid.verifier_management.mappers.ManagementMapper.managementToManagementDto;

@RestController
@AllArgsConstructor
public class VerifierManagementController {

    private final ManagementService presentationService;

    @PostMapping("/verifications")
    @Operation(summary = "Creates a new verification process with the given attributes")
    public CreateManagementResponseDto createVerification(@Valid @RequestBody CreateManagementRequestDto requestDto) {

        Management verificationManagement = presentationService.createVerificationManagement(requestDto);

        return managementToManagementDto(verificationManagement);
    }

    /*@GetMapping("/verifications/{verificationId}")
    @Operation(summary = "Returns the state of the verification & if applicable the data that was sent from the holder")
    public GetVerificationResponseDto getVerification(@PathVariable UUID verificationId) {

        VerificationManagement verificationManagement = presentationService.getVerification(verificationId);

        AuthorizationResponseData authorizationResponseData = authDataService.getAuthorizationResponseData(verificationManagement.getAuthorizationRequestId());

        return verificationManagementToCreateVerificationResponseDto(verificationManagement, authorizationResponseData);
    }*/
}
