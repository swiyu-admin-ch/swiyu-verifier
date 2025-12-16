/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@SuppressWarnings("java:S116")
// Note: For Spring to correctly parse x-www-url-encoded payload the field must be named the same as the field. JsonProperty does not work for these.

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerificationPresentationUnion",
        description = "Union DTO that contains all possible parameters from all verification presentation types. " +
                "Only the relevant fields should be populated based on the request type.")
public class VerificationPresentationUnionDto {

    // From VerificationPresentationRequestDto (Standard/PE) and VerificationPresentationDCQLRequestDto
    @Schema(
            description = "VP token that can be either a string for standard presentations or a JSON object for DCQL presentations. " +
                    "For standard/PE presentations: JWT token string. " +
                    "For DCQL presentations: Object containing credential query results where keys are query IDs and values are arrays of presentations.",
            example = """
                    Standard/PE: "eyJhbGci...QMA"
                    DCQL: {"my_credential": ["eyJhbGci...QMA", "eyJhbGci...QMA"]}
                    """,
            oneOf = {String.class, Map.class}
    )
    protected Object vp_token;

    @Schema(
            description = "The presentation submission as defined in DIF presentation submission (used for Standard and PE presentations)",
            example = "{\"id\":\"a30e3b91-fb77-4d22-95fa-871689c322e2\",\"definition_id\":\"32f54163-7166-48f1-93d8-ff217bdb0653\"}"
    )
    protected String presentation_submission;

    // From VerificationPresentationRejectionDto
    @Schema(
            description = "Error code for rejection (used for REJECTION)",
            example = "client_rejected"
    )
    protected VerificationClientErrorDto error;

    @Schema(
            description = "Error description for rejection (used for REJECTION)",
            example = "The owner has declined the verification request."
    )
    protected String error_description;


    // From VerificationPresentationDCQLRequestEncryptedDto
    @Schema(
            description = "Encrypted response JWE string (used for DCQLE)",
            example = "eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMjU2R0NNIiwidHlwIjoiSldFIn0..."
    )
    protected String response;

    // Helper methods to determine the type of request
    @JsonIgnore
    public boolean isStandardPresentation() {
        return vp_token instanceof String && presentation_submission != null;
    }

    @JsonIgnore
    public boolean isRejection() {
        return error != null;
    }

    @JsonIgnore
    public boolean isDcqlPresentation() {
        return vp_token != null && presentation_submission == null;
    }

    /**
     * Validate if the presentation is properly encrypted presentation
     * @return false if response is missing or response is present but additional unencrypted data has been sent
     */
    @JsonIgnore
    public boolean isEncryptedPresentation() {
        return response != null && error == null && error_description == null && presentation_submission == null && vp_token == null;
    }

    // Factory methods to extract specific DTOs from Union DTO
    @JsonIgnore
    public VerificationPresentationRequestDto toStandardPresentation() {
        if (!isStandardPresentation()) {
            throw new IllegalArgumentException("Union DTO does not contain standard presentation data");
        }
        var dto = new VerificationPresentationRequestDto();
        dto.setVpToken((String) this.vp_token);
        dto.setPresentationSubmission(this.presentation_submission);
        return dto;
    }

    @JsonIgnore
    public VerificationPresentationRejectionDto toRejection() {
        if (!isRejection()) {
            throw new IllegalArgumentException("Union DTO does not contain rejection data");
        }
        var dto = new VerificationPresentationRejectionDto();
        dto.setError(this.error);
        dto.setErrorDescription(this.error_description);
        return dto;
    }

    @JsonIgnore
    public VerificationPresentationDCQLRequestDto toDcqlPresentation() {
        if (!isDcqlPresentation()) {
            throw new IllegalArgumentException("Union DTO does not contain DCQL presentation data");
        }
        var dto = new VerificationPresentationDCQLRequestDto();

        // Convert vp_token to Map<String, List<String>> for DCQL presentations
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, List<String>> mapToken;

            if (this.vp_token instanceof String vpTokenString) {
                // If vp_token is a JSON string, parse it first
                mapToken = objectMapper.readValue(vpTokenString, new TypeReference<>() {
                });
            } else {
                // If vp_token is already an object, convert it directly
                mapToken = objectMapper.convertValue(this.vp_token, new TypeReference<>() {
                });
            }

            dto.setVpToken(mapToken);
            return dto;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse vp_token as DCQL format: " + e.getMessage(), e);
        }
    }
}
