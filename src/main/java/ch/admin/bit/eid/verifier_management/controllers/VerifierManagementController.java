package ch.admin.bit.eid.verifier_management.controllers;

import ch.admin.bit.eid.verifier_management.config.ApplicationConfig;
import ch.admin.bit.eid.verifier_management.mappers.ManagementMapper;
import ch.admin.bit.eid.verifier_management.models.dto.CreateManagementRequestDto;
import ch.admin.bit.eid.verifier_management.models.dto.ManagementResponseDto;
import ch.admin.bit.eid.verifier_management.services.ManagementService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@AllArgsConstructor
public class VerifierManagementController {

    private final ManagementService presentationService;

    private final ApplicationConfig applicationConfig;

    @PostMapping("/verifications")
    @Operation(summary = "Creates a new verification process with the given attributes")
    public ManagementResponseDto createVerification(@Valid @RequestBody CreateManagementRequestDto requestDto) {

        return ManagementMapper.toDto(presentationService.createVerificationManagement(requestDto), applicationConfig.getOid4vpUrl());
    }

    @GetMapping("/verifications/{verificationId}")
    public ManagementResponseDto getVerification(@PathVariable UUID verificationId) {
        return ManagementMapper.toDto(presentationService.getManagement(verificationId), applicationConfig.getOid4vpUrl());
    }
}
