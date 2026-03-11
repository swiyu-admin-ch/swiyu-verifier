package ch.admin.bj.swiyu.verifier.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Transport DTO representing the union of all possible wallet response shapes.
 * <p>
 * This type is intentionally kept free of mapping logic. Use {@link VerificationPresentationMapper}
 * to convert instances of this class into dedicated DTOs.
 */
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
    private Object vp_token;

    @Schema(
            description = "The presentation submission as defined in DIF presentation submission (used for Standard and PE presentations)",
            example = "{\"id\":\"a30e3b91-fb77-4d22-95fa-871689c322e2\",\"definition_id\":\"32f54163-7166-48f1-93d8-ff217bdb0653\"}"
    )
    private String presentation_submission;

    // From VerificationPresentationRejectionDto
    @Schema(
            description = "Error code for rejection (used for REJECTION)",
            example = "client_rejected"
    )
    private VerificationClientErrorDto error;

    @Schema(
            description = "Error description for rejection (used for REJECTION)",
            example = "The owner has declined the verification request."
    )
    private String error_description;


    // From VerificationPresentationDCQLRequestEncryptedDto
    @Schema(
            description = "Encrypted response JWE string (used for DCQLE)",
            example = "eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMjU2R0NNIiwidHlwIjoiSldFIn0..."
    )
    private String response;

    @Schema(description = """
                    The OAuth State is an opaque value used by the client to maintain state between the request and callback.<br>
                    If provided in the request object the state string <em>MUST</em> be returned in the response.
                    """)
    private String state;

    // Helper methods to determine the type of request
    /**
     * @return {@code true} if this payload represents a presentation exchange response
     * (i.e., {@code vp_token} is a string and {@code presentation_submission} is present).
     */
    @JsonIgnore
    @SuppressWarnings("java:S1845")
    public boolean isPresentationExchange() {
        return vp_token instanceof String && presentation_submission != null;
    }

    /**
     * @return {@code true} if this payload represents a rejection response (error + description).
     */
    @JsonIgnore
    public boolean isRejection() {
        return error != null;
    }

    /**
     * @return {@code true} if this payload represents a DCQL presentation (no presentation_submission, but vp_token present).
     */
    @JsonIgnore
    public boolean isDcqlPresentation() {
        return vp_token != null && presentation_submission == null;
    }

    /**
     * Validates whether the payload represents an encrypted DCQL presentation.
     *
     * @return {@code true} if response is present and no additional unencrypted fields are set
     */
    @JsonIgnore
    public boolean isEncryptedPresentation() {
        return response != null && error == null && error_description == null && presentation_submission == null && vp_token == null;
    }

}
