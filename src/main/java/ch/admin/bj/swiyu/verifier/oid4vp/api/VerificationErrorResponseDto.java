/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;


/**
 * Represents a verification error.
 *
 * @param error
 * @param errorCode
 * @param errorDescription
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "VerificationErrorResponse")
public record VerificationErrorResponseDto(
        @NotNull
        VerificationErrorDto error,
        @JsonProperty("error_code")
        VerificationErrorResponseCodeDto errorCode,
        @JsonProperty("error_description")
        String errorDescription
) {
}
