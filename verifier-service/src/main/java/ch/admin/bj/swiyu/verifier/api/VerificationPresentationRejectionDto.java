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
public class VerificationPresentationRejectionDto {

    @Schema(
            description = "Error code as defined in [OpenId4VP error response section](https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#name-error-response)"
    )
    private VerificationClientErrorDto error;

    @Schema(
            description = "Error description as seems fit"
    )
    private String error_description;
}

