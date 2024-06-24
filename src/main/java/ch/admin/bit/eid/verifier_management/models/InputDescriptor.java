package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.models.dto.ConstraintDto;
import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.Id;

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

    private HashMap<String, FormatAlgorithmDto> format;

    private List<ConstraintDto> constraints;
}
