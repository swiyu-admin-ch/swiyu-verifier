/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.management.api.ApiErrorDto;
import ch.admin.bj.swiyu.verifier.management.api.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.management.api.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.management.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.management.domain.exception.VerificationNotFoundException;
import ch.admin.bj.swiyu.verifier.management.service.ManagementService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@RequestMapping(value="/api/v1/verifications")
public class VerifierManagementController {

    private final ManagementService presentationService;

    private final ApplicationProperties applicationProperties;

    @Timed
    @PostMapping(value = { ""})
    @Operation(
            summary = "Creates a new verification process with the given attributes",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Verification created",
                            content = @Content(schema = @Schema(implementation = ManagementResponseDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request. The request body is not valid",
                            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
                    )
            }
    )
    public ManagementResponseDto createVerification(@Valid @RequestBody CreateVerificationManagementDto requestDto) {
        return presentationService.createVerificationManagement(requestDto);
    }

    @Timed
    @GetMapping(value= {"/{verificationId}"})
    @Operation(
            summary = "Get verification by id",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Verification found",
                            content = @Content(schema = @Schema(implementation = ManagementResponseDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Verification not found or already expired",
                            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
                    )
            }
    )
    public ManagementResponseDto getVerification(@PathVariable UUID verificationId) {
        return presentationService.getManagement(verificationId);
    }

    @ExceptionHandler(VerificationNotFoundException.class)
    ResponseEntity<ApiErrorDto> handleVerificationNotFoundException(final VerificationNotFoundException exception) {
        log.info("Verification not found for id: {}", exception.getManagementId());
        return new ResponseEntity<>(new ApiErrorDto(NOT_FOUND, exception.getMessage()), NOT_FOUND);
    }
}
