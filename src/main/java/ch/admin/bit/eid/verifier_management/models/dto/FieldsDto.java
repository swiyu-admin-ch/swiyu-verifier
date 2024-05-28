package ch.admin.bit.eid.verifier_management.models.dto;

import lombok.Data;

import java.util.List;

@Data
public class FieldsDto {
    private List<ConstraintDto> fields;
}
