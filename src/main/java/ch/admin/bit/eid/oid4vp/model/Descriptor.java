package ch.admin.bit.eid.oid4vp.model;

import ch.admin.bit.eid.oid4vp.model.validations.ValidJsonPath;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Getter
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
