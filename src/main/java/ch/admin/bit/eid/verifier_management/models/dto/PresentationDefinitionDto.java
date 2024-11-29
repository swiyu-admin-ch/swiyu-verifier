package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.models.validations.NullOrFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Schema(name = "PresentationDefinition")
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresentationDefinitionDto {

    @Schema(description = "A unique ID for the desired context. Can be any String, but using a UUID is recommended.", example = "00000000-0000-0000-0000-000000000000")
    private String id;

    @Schema(description = "(Optional) If present human-friendly string intended to constitute a distinctive designation of the Presentation Definition", example = "Test Verification")
    private String name;

    @Schema(description = "(Optional) Purpose for which the presentation definition is requested and used for", example = "We want to test a new Verifier")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format.", example = """
            {"vc+sd-jwt": {"sd-jwt_alg_values":["ES256"], "kb-jwt_alg_values":["ES256"]}}
            """)
    @NullOrFormat
    private Map<String, FormatAlgorithmDto> format;

    @Valid
    @NotNull
    @JsonProperty("input_descriptors")
    @Schema(description = "Input Descriptors are objects used to describe the information a Verifier requires of a Holder")
    private List<InputDescriptorDto> inputDescriptors;
}
