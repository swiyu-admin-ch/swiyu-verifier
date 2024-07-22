package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PresentationDefinition implements Serializable {

    @Id
    private String id;

    private String name;

    private String purpose;

    @JsonProperty("input_descriptors")
    private List<InputDescriptorDto> inputDescriptors;
}
