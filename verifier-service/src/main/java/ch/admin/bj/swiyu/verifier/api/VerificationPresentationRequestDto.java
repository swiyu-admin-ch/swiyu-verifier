/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerificationPresentationRequest")
public class VerificationPresentationRequestDto {

    @Schema(
            description = "Submitted token (e.g. of the wallet) according to " +
                    "[OpenID4VP specification](https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#section-6.1)"
    )
    private String vp_token;


    /**
     * Note: Why string - we do not use PresentationSubmissionDto here, because we support invalid
     * submissions. We will parse the JSON and if it is invalid we apply propper
     * error handling (e.g. updating the entity).
     */
    @Schema(
            description = "The presentation submission as defined in [DIF presentation submission](https://identity.foundation/presentation-exchange/#presentation-submission)"
    )
    private String presentation_submission;

    /**
     * @deprecated These error properties are deprecated. Use the dedicated rejection endpoint instead:
     * POST /oid4vp/api/request-object/{request_id}/response-data-rejection
     * with VerificationPresentationRejectionDto
     */
    @Deprecated
    @Schema(
            description = "Error code as defined in [OpenId4VP error response section](https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#name-error-response)",
            deprecated = true
    )
    private VerificationClientErrorDto error;

    /**
     * @deprecated These error properties are deprecated. Use the dedicated rejection endpoint instead:
     * POST /oid4vp/api/request-object/{request_id}/response-data-rejection
     * with VerificationPresentationRejectionDto
     */
    @Deprecated
    @Schema(
            description = "Error description as seems fit",
            deprecated = true
    )
    private String error_description;

    /**
     * @deprecated This method is deprecated. Use the dedicated rejection endpoint instead:
     * POST /oid4vp/api/request-object/{request_id}/response-data-rejection
     */
    @Deprecated
    @JsonIgnore
    public boolean isClientRejection() {
        return error != null;
    }

}
