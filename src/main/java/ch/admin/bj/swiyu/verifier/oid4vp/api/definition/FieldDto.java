/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.api.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Field")
public class FieldDto {

    @NotEmpty
    @Schema(description = "(Mandatory) Array of one or more JSONPath string expressions")
    private List<String> path;

    @Schema(description = "(Optional) If present value MUST be a string that is unique")
    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) If present describes purpose for which the field is requested")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
    private FilterDto filter;

    // TODO other fields are currently ignored -> check https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor
}
