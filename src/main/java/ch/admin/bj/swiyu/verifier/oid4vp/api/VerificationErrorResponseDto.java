/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        VerificationErrorResponseCodeDto errorCode,
        String errorDescription
) {
}
