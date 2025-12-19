/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("vp_token")
    private String vpToken;


    /**
     * Note: Why string - we do not use PresentationSubmissionDto here, because we support invalid
     * submissions. We will parse the JSON and if it is invalid we apply propper
     * error handling (e.g. updating the entity).
     */
    @Schema(
            description = "The presentation submission as defined in [DIF presentation submission](https://identity.foundation/presentation-exchange/#presentation-submission)"
    )
    @JsonProperty("presentation_submission")
    private String presentationSubmission;

}
