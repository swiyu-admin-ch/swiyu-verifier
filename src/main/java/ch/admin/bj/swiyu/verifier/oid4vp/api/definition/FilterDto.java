/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.api.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Filter")
public class FilterDto {

    // According to current interop profile only exact string match is supported
    // hence the combination of type: string and const: <filter value>
    @Pattern(regexp = "string", message = "Only filter of type 'string' is supported")
    @Schema(description = "(Optional) If present value MUST be 'string'", defaultValue = "string")
    private String type;

    @JsonProperty("const")
    @Schema(description = "(Optional) If present value MUST be a string / no pattern")
    private String constDescriptor;
}
