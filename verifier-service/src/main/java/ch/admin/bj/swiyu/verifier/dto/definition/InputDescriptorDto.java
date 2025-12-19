/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.dto.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;

import java.util.Map;

@Schema(name = "InputDescriptor")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InputDescriptorDto(
        @Id
        @NotBlank(message = "id of input descriptor is mandatory")
        @Schema(description = "(Mandatory) unique string with no conflict with another id in the Presentation Definition", example = "11111111-1111-1111-1111-111111111111")
        String id,

        @Schema(description = "(Optional) If present human-friendly name which describes the target field", example = "Example Data Request")
        String name,

        @Schema(description = "(Optional) Purpose for which the data is requested", example = "We collect this data to test our verifier")
        String purpose,

        @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format", example = """
                {"vc+sd-jwt": {"sd-jwt_alg_values":["ES256"], "kb-jwt_alg_values":["ES256"]}}
                """)
        @NullOrValidFormatAlgorithm
        Map<String, FormatAlgorithmDto> format,

        @NotNull
        @Valid
        ConstraintDto constraints
) {
}
