/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.verifier.api.VerificationErrorResponseDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRejectionDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationDCQLRequestEncryptedDto;
import ch.admin.bj.swiyu.verifier.api.metadata.OpenidClientMetadataDto;
import ch.admin.bj.swiyu.verifier.api.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.service.OpenIdClientMetadataConfiguration;
import ch.admin.bj.swiyu.verifier.service.oid4vp.RequestObjectService;
import ch.admin.bj.swiyu.verifier.service.oid4vp.VerificationService;
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

import java.util.UUID;

/**
 * OpenID4VC Issuance Controller
 * <p>
 * Implements the OpenID4VCI defined endpoints
 * <a href="https://openid.github.io/OpenID4VCI/openid-4-verifiable-credential-issuance-wg-draft.html">OID4VCI Spec</a>
 */
@RestController
@AllArgsConstructor
@Slf4j
@Tag(name = "Verfifier OID4VP API",
        description = "Handles OpenID for Verifiable Presentations (OID4VP) endpoints, enabling verifiers to retrieve " +
                "request objects, receive verification presentations, and access OpenID client metadata as specified " +
                "by the OID4VP protocol. This API is intended for wallets to fetch credentials " +
                "in compliance with OpenID standards. (IF-101)")
@RequestMapping({"/oid4vp/api/"})
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
    public OpenidClientMetadataDto getOpenIdClientMetadata() {
        return openIdClientMetadataConfiguration.getOpenIdClientMetadata();
    }

    @Timed
    @GetMapping(value = {"request-object/{request_id}"}, produces = {"application/oauth-authz-req+jwt", MediaType.APPLICATION_JSON_VALUE})
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
    public ResponseEntity<Object> getRequestObject(@PathVariable(name = "request_id") UUID requestId) {
        var requestObject = requestObjectService.assembleRequestObject(requestId);
        var responseBuilder = ResponseEntity.ok();
        if (requestObject instanceof String) {
            // JWT Request Object
            responseBuilder.contentType(new MediaType("application", "oauth-authz-req+jwt"));
        } else {
            // Unsecured Request Object
            responseBuilder.contentType(MediaType.APPLICATION_JSON);
        }
        return responseBuilder.body(requestObject);
    }

    @Timed
    @PostMapping(value = {"request-object/{request_id}/response-data"},
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Receive Verification Presentation (from e.g. Wallet)",
            externalDocs = @ExternalDocumentation(
                    description = "OpenId4VP response parameters",
                    url = "https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#section-6.1"
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
        verificationService.receiveVerificationPresentationDCQLEncrypted(requestId, request);
    }

    @Timed
    @PostMapping(value = {"request-object/{request_id}/response-data-rejection"},
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Reject Verification Presentation (from e.g. Wallet)",
            externalDocs = @ExternalDocumentation(
                    description = "OpenId4VP response parameters",
                    url = "https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#section-6.1"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Verification Presentation rejection received"
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
    public void verificationPresentationClientRejection(
            @PathVariable(name = "request_id") UUID requestId,
            VerificationPresentationRejectionDto request) {
        verificationService.receiveVerificationPresentationClientRejection(requestId, request);
    }

    @Timed
    @PostMapping(value = {"request-object/{request_id}/response-data-dcql"},
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Receive Verification Presentation DCQL (from e.g. Wallet) - NOT YET IMPLEMENTED",
            description = "⚠️ **PLACEHOLDER ENDPOINT** - This endpoint is not yet implemented and serves only to publish the interface specification. " +
                    "Calling this endpoint will result in an UnsupportedOperationException. " +
                    "Implementation will be provided in a future release. " +
                    "This endpoint is intended to handle DCQL verification presentations with VP token as object structure according to the DCQL specification.",
            externalDocs = @ExternalDocumentation(
                    description = "OpenId4VP response parameters for DCQL",
                    url = "https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-8.1"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "DCQL Verification Presentation received and processed (NOT CURRENTLY AVAILABLE)"
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
    public void receiveVerificationPresentationDCQL(
            @PathVariable(name = "request_id") UUID requestId,
            VerificationPresentationDCQLRequestDto request) {
        verificationService.receiveVerificationPresentationDCQL(requestId, request);
    }

    @Timed
    @PostMapping(value = {"request-object/{request_id}/response-data-dcql-encrypted"},
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE},
            produces = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Receive Verification Presentation DCQL Encrypted (from e.g. Wallet) - NOT YET IMPLEMENTED",
            description = "⚠️ **PLACEHOLDER ENDPOINT** - This endpoint is not yet implemented and serves only to publish the interface specification. " +
                    "Calling this endpoint will result in an UnsupportedOperationException. " +
                    "Implementation will be provided in a future release. " +
                    "This endpoint is intended to handle encrypted DCQL verification presentations with response as encrypted string.",
            externalDocs = @ExternalDocumentation(
                    description = "OpenId4VP response parameters for encrypted DCQL",
                    url = "https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#section-6.1"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Encrypted DCQL Verification Presentation received and processed (NOT CURRENTLY AVAILABLE)"
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
    public void receiveVerificationPresentationDCQLEncrypted(
            @PathVariable(name = "request_id") UUID requestId,
            VerificationPresentationDCQLRequestEncryptedDto request) {
        verificationService.receiveVerificationPresentationDCQLEncrypted(requestId, request);
    }
}