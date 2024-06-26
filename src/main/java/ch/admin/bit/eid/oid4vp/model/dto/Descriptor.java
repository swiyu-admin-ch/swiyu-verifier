package ch.admin.bit.eid.oid4vp.model.dto;

import ch.admin.bit.eid.oid4vp.model.validations.ValidJsonPath;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Descriptor {

    @NotNull
    private String id;

    @NotBlank
    private String format;

    @NotBlank
    @ValidJsonPath
    private String path;

    @JsonProperty("path_nested")
    private Descriptor pathNested;
}
