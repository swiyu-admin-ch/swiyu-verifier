package ch.admin.bit.eid.oid4vp.model.persistence;

import ch.admin.bit.eid.oid4vp.model.dto.FormatAlgorithm;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;


@Builder
@Data
@AllArgsConstructor
@NotEmpty
// TODO check
public class PresentationDefinition implements Serializable {

    private String id;

    @Schema(description = "(Optional) If present human-friendly name which describes the target field")
    private String name;

    @Schema(description = "(Optional) Purpose for which the presentation definition is requested")
    private String purpose;

    @Schema(description = "(Optional) If present object with one or more properties matching the registered Claim Format")
    private HashMap<String, FormatAlgorithm> format;

    @Valid
    @NotNull
    @JsonProperty("input_descriptors")
    private List<InputDescriptor> inputDescriptors;
}
