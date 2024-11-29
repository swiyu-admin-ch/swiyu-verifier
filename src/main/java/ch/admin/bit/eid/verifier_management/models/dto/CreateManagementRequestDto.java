package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Schema(name = "CreateManagementRequest")
@Getter
public class CreateManagementRequestDto {

    @Valid
    @NotEmpty
    @JsonProperty("input_descriptors")
    private List<InputDescriptorDto> inputDescriptors;
}
