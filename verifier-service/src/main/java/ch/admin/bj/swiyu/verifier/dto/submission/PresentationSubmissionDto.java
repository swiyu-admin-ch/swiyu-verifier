/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.dto.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * See <a href="https://identity.foundation/presentation-exchange/spec/v2.0.0/#presentation-submission">presentation-submission.</a>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "PresentationSubmission")
public class PresentationSubmissionDto {

    @NotBlank(message = "Presentation submission id is mandatory")
    private String id;

    @JsonProperty("definition_id")
    private String definitionId;

    @JsonProperty("descriptor_map")
    @NotEmpty(message = "DescriptorDto map cannot be empty")
    private List<@Valid DescriptorDto> descriptorMap;
}
