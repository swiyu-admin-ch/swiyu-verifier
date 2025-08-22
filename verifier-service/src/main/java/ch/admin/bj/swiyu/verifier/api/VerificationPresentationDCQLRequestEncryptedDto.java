/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "VerificationPresentationDCQLRequestEncrypted", description = "Encrypted DCQL verification presentation request with response string")
public class VerificationPresentationDCQLRequestEncryptedDto {

    @Schema(
            description = "Encrypted response JWE string containing the DCQL verification data",
            example = "eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMjU2R0NNIiwidHlwIjoiSldFIn0..."
    )
    private String response;

}
