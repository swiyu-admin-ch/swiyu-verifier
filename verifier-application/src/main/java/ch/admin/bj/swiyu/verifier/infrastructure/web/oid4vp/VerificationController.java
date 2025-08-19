/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp;

import ch.admin.bj.swiyu.verifier.api.ApiErrorDto;
import ch.admin.bj.swiyu.verifier.api.VerificationPresentationRequestDto;
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
                            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
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
                            content = @Content(schema = @Schema(implementation = ApiErrorDto.class))
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
}