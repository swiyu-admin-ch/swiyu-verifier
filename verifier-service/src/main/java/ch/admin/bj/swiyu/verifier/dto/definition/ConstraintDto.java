/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.dto.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

@Schema(name = "Constraint")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConstraintDto(
        @Schema(description = "(Optional) unique string with no conflict with another id in the Presentation Definition")
        String id,

        @Schema(description = "(Optional) If present human-friendly name which describes the target field")
        String name,

        @Schema(description = "(Optional) Purpose for which the data is requested")
        String purpose,

        @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
        Map<String, FormatAlgorithmDto> format,

        @NotNull
        @NotEmpty
        @Schema(description = "Selection which properties are requested of the holder", example = """
                [{"path": ["$.vct"],"filter":{"type": "string","const":"test-sdjwt"}},{"path":["$.dateOfBirth"]}]
                """)
        List<@Valid FieldDto> fields
) {

}
