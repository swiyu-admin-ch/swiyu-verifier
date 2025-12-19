/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;


/**
 * Represents a verification error.
 * @deprecated For future releases: Use {@link ApiErrorDto} instead (as soon as wallet migrated).
 * @param error
 * @param errorCode
 * @param errorDescription
 */
@Deprecated(forRemoval = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "VerificationErrorResponse")
public record VerificationErrorResponseDto(
        @NotNull
        VerificationErrorTypeDto error,
        @JsonProperty("detail")
        VerificationErrorResponseCodeDto detail,
        // to cause no breaking changes also the old name can be used (as soon as the wallet migrated to the new name,
        // this can be removed and the APIErrorDto can be used directly)
        @JsonProperty("error_code")
        VerificationErrorResponseCodeDto errorCode,
        @JsonProperty("error_description")
        String errorDescription
) {
}