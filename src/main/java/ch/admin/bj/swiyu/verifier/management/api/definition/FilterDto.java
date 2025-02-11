/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.api.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

@Schema(name = "Filter")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilterDto(
        @Pattern(regexp = "string", message = "Only filter of type 'string' is supported")
        @Schema(description = "(Optional) If present value MUST be 'string'", example = "string", defaultValue = "string")
        String type,

        @JsonProperty("const")
        @Schema(description = "(Optional) If present value MUST be a string / no pattern", example = "vct-as-in-issuer-metadata")
        String constDescriptor
) {
}
