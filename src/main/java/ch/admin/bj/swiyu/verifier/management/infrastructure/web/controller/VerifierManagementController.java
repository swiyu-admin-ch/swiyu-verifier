package ch.admin.bj.swiyu.verifier.management.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.management.api.ApiErrorDto;
import ch.admin.bj.swiyu.verifier.management.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.management.api.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.management.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.management.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.management.service.ManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@AllArgsConstructor
@Slf4j
@Tag(name = "OID4VP Management API")
public class VerifierManagementController {

    private final ManagementService presentationService;

    private final ApplicationProperties applicationProperties;

    @PostMapping("/verifications")
    @Operation(summary = "Creates a new verification process with the given attributes")
    public ManagementResponseDto createVerification(@Valid @RequestBody CreateVerificationManagementDto requestDto) {
        return presentationService.createVerificationManagement(requestDto);
    }

    @GetMapping("/verifications/{verificationId}")
    public ManagementResponseDto getVerification(@PathVariable UUID verificationId) {
        return presentationService.getManagement(verificationId);
    }

    @ExceptionHandler(VerificationNotFoundException.class)
    ResponseEntity<ApiErrorDto> handleVerificationNotFoundException(final VerificationNotFoundException exception) {
        log.info("Verification not found for id: {}", exception.getManagementId());
        return new ResponseEntity<>(new ApiErrorDto(NOT_FOUND, exception.getMessage()), NOT_FOUND);
    }
}
