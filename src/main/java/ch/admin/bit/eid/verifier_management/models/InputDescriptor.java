package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.models.dto.ConstraintDto;
import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import lombok.*;

import java.io.Serializable;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputDescriptor implements Serializable {

    private String id;

    private String name;

    private String purpose;

    private Map<String, FormatAlgorithmDto> format;

    private List<ConstraintDto> constraints;
}
