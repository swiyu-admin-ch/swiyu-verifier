package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.models.dto.ConstraintDto;
import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputDescriptor implements Serializable {

    private String id;

    private String name;

    private String purpose;

    private Map<String, FormatAlgorithmDto> format;

    private ConstraintDto constraints;
}
