/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.api.definition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a presentation definition as defined in the OpenID Connect for Verifiable Presentations specification.
 * <p>
 * See <a href="https://identity.foundation/presentation-exchange/spec/v2.0.0/#presentation-definition">presentation-definition</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PresentationDefinition")
public class PresentationDefinitionDto {

    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) Purpose for which the presentation definition is requested")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
    private Map<String, FormatAlgorithmDto> format;

    @Valid
    @NotNull
    @JsonProperty("input_descriptors")
    private List<InputDescriptorDto> inputDescriptors;
}
