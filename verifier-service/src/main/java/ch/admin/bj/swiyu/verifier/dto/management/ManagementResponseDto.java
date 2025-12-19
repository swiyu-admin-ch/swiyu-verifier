/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.dto.management;

import ch.admin.bj.swiyu.verifier.dto.definition.PresentationDefinitionDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.DcqlQueryDto;
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
        @JsonProperty("dcql_query")
        DcqlQueryDto dcqlQuery,
        @JsonProperty("wallet_response")
        ResponseDataDto walletResponse,
        @JsonProperty("verification_url")
        String verificationUrl,
        @JsonProperty("verification_deeplink")
        String verificationDeeplink
) {
}