/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(name = "Field")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldDto(
        @NotEmpty
        @Schema(description = "(Mandatory) Array of one or more JSONPath string expressions")
        List<String> path,

        @Schema(description = "(Optional) If present value MUST be a string that is unique")
        String id,

        @Schema(description = "(Optional) If present human-friendly name which describes the target field")
        String name,

        @Schema(description = "(Optional) If present describes purpose for which the field is requested")
        String purpose,

        @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
        FilterDto filter
) {
    // Note: some other fields are currently ignored -> check https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor
}
