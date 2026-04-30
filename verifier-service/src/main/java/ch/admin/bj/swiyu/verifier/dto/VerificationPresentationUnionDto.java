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
     * @return {@code true} if this payload represents a rejection response (error + description).
     */
    @JsonIgnore
    public boolean isRejection() {
        return error != null;
    }

    /**
     * @return {@code true} if this payload represents a DCQL presentation (vp_token present).
     */
    @JsonIgnore
    public boolean isUnencryptedDcqlPresentation() {
        return vp_token != null;
    }

    /**
     * Validates whether the payload represents an encrypted DCQL presentation.
     *
     * @return {@code true} if response is present and no additional unencrypted fields are set
     */
    @JsonIgnore
    public boolean isEncryptedPresentation() {
        return response != null && error == null && error_description == null && vp_token == null;
    }

}
