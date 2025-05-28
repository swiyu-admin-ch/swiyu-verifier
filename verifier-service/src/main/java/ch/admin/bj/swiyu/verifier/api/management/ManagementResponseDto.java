/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api.management;

import ch.admin.bj.swiyu.verifier.api.definition.PresentationDefinitionDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "ManagementResponse")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ManagementResponseDto(
        UUID id,
        @JsonProperty("request_nonce")
        String requestNonce,
        VerificationStatusDto state,
        @JsonProperty("presentation_definition")
        PresentationDefinitionDto presentationDefinition,
        @JsonProperty("wallet_response")
        ResponseDataDto walletResponse,
        @JsonProperty("verification_url")
        String verificationUrl
) {
}
