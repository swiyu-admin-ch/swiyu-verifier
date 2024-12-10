package ch.admin.bj.swiyu.verifier.oid4vp.api.submission;

import ch.admin.bj.swiyu.verifier.oid4vp.common.json.ValidJsonPath;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(name = "Descriptor")
public class DescriptorDto {

    private String id;

    @NotBlank
    private String format;

    @NotBlank
    @ValidJsonPath
    private String path;

    @Valid
    @JsonProperty("path_nested")
    private DescriptorDto pathNested;
}
