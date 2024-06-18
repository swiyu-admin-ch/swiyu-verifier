package ch.admin.bit.eid.oid4vp.model.persistence;

import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import jakarta.validation.constraints.NotEmpty;
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

    @Id
    private UUID id;

    private List<InputDescriptor> inputDescriptors;

    // https://identity.foundation/presentation-exchange/#submission-requirements
    /**
     * Optional
     */
    private HashMap<String, Object> submissionRequirements;
}
