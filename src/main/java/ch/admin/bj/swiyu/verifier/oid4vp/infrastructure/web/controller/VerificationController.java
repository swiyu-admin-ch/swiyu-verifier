/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.oid4vp.api.VerificationErrorResponseDto;
import ch.admin.bj.swiyu.verifier.oid4vp.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.oid4vp.api.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.oid4vp.common.config.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.oid4vp.service.RequestObjectService;
import ch.admin.bj.swiyu.verifier.oid4vp.service.VerificationService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

import static ch.admin.bj.swiyu.verifier.oid4vp.service.VerificationMapper.toVerficationErrorResponseDto;

/**
 * OpenID4VC Issuance Controller
 * <p>
 * Implements the OpenID4VCI defined endpoints
 * <a href="https://openid.github.io/OpenID4VCI/openid-4-verifiable-credential-issuance-wg-draft.html">OID4VCI Spec</a>
 */
@RestController
@AllArgsConstructor
@Slf4j
@Tag(name = "OID4VP Verfifier API")
@RequestMapping({ "/api/v1/"})
public class VerificationController {

    private final RequestObjectService requestObjectService;
    private final VerificationService verificationService;
    private final OpenIdClientMetadataConfiguration openIdClientMetadataConfiguration;

    @Timed
    @GetMapping(value = {"openid-client-metadata.json"})
    @Operation(
            summary = "Get client metadata",
            description = "Metadata providing further information about the verifier, such as name and logo.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request object either as plaintext or signed JWT",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = {
                                            @ExampleObject(name = "Sample Client Metadata", value = """
                                                    {
                                                        "client_id": "did:example:12345",
                                                        "client_name#en": "English name (all regions)",
                                                        "client_name#fr": "French name (all regions)",
                                                        "client_name#de-DE": "German name (region Germany)",
                                                        "client_name#de-CH": "German name (region Switzerland)",
                                                        "client_name#de": "German name (fallback)",
                                                        "client_name": "Fallback name",
                                                        "client_logo": "www.example.com/logo.png",
                                                        "client_logo#fr": "www.example.com/logo_fr.png"
                                                    }""")
                                    }
                            )
                    )})
    public Map<String, Object> getOpenIdClientMetadata() {
        return openIdClientMetadataConfiguration.getOpenIdClientMetadata();
    }

    @Timed
    @GetMapping(value= {"request-object/{request_id}"})
    @Operation(
            summary = "Get Request Object",
            description = "Can return a RequestObjectDto as JSON Object or a SignedJwt String depending of JAR (JWT secured authorization request) flag in verifier management",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request object either as plaintext or signed JWT",
                            content = @Content(
                                    schema = @Schema(implementation = RequestObjectDto.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Request Object not found",
                            content = @Content(schema = @Schema(implementation = VerificationErrorResponseDto.class))
                    )
            }
    )
    public Object getRequestObject(@PathVariable(name = "request_id") UUID requestId) {
        return requestObjectService.assembleRequestObject(requestId);
    }

    @Timed
    @PostMapping(value = {"request-object/{request_id}/response-data"},
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Receive Verification Presentation (from e.g. Wallet)",
            externalDocs = @ExternalDocumentation(
                    description = "OpenId4VP response parameters",
                    url = "https://openid.net/specs/openid-4-verifiable-presentations-1_0-20.html#section-6.1"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Verification Presentation received"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Bad Request. The request body is not valid",
                            content = @Content(schema = @Schema(implementation = VerificationErrorResponseDto.class))
                    )
            }
    )
    @ResponseStatus(HttpStatus.OK)
    @RequestBody(content = @Content(mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE))
    public void receiveVerificationPresentation(
            @PathVariable(name = "request_id") UUID requestId,
            VerificationPresentationRequestDto request) {
        verificationService.receiveVerificationPresentation(requestId, request);
    }

    @ExceptionHandler(VerificationException.class)
    ResponseEntity<VerificationErrorResponseDto> handleVerificationException(VerificationException e) {
        var error = toVerficationErrorResponseDto(e);
        log.warn("The received verification presentation could not be verified - caused by {}-{}:{}", error.error(), error.errorCode(), error.errorDescription(), e);
        HttpStatus httpStatus;
        switch (e.getErrorType()) {
            case VERIFICATION_PROCESS_CLOSED -> httpStatus = HttpStatus.GONE;
            case AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND -> httpStatus = HttpStatus.NOT_FOUND;
            default -> httpStatus = HttpStatus.BAD_REQUEST;
        }
        return new ResponseEntity<>(error, httpStatus);
    }
}