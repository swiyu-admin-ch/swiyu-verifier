package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PresentationDefinition {

    @Id
    private String id;

    private String name;

    private String purpose;

    private Map<String, FormatAlgorithmDto> format;

    private List<InputDescriptorDto> inputDescriptors;
}
