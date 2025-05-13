/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.domain.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.annotation.Id;

import java.util.List;
import java.util.Map;

/**
 * Represents a presentation definition as defined in the OpenID Connect for Verifiable Presentations specification.
 * <p>
 * See <a href="https://identity.foundation/presentation-exchange/spec/v2.0.0/#presentation-definition">presentation-definition</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PresentationDefinition(
        String id,
        String name,
        String purpose,
        Map<String, FormatAlgorithm> format,
        @Valid
        @NotNull
        @JsonProperty("input_descriptors")
        List<InputDescriptor> inputDescriptors
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FormatAlgorithm(
            @NotNull
            @NotEmpty
            @JsonProperty("sd-jwt_alg_values")
            List<String> alg,
            @NotNull
            @NotEmpty
            @JsonProperty("kb-jwt_alg_values")
            List<String> keyBindingAlg
    ) {
    }

    public record InputDescriptor(
            @Id
            @NotBlank(message = "id of input descriptor is mandatory")
            String id,
            String name,
            String purpose,
            Map<String, FormatAlgorithm> format,
            @Valid
            @NotNull
            Constraint constraints
    ) {
    }

    public record Constraint(
            String id,
            String name,
            String purpose,
            Map<String, FormatAlgorithm> format,
            @NotNull
            List<@Valid Field> fields
    ) {
    }

    public record Field(
            @NotEmpty
            List<String> path,
            String id,
            String name,
            String purpose,
            Filter filter
    ) {
    }

    public record Filter(
            @Pattern(regexp = "string", message = "Only filter of type 'string' is supported")
            String type,
            @JsonProperty("const")
            String constDescriptor
    ) {
    }
}
