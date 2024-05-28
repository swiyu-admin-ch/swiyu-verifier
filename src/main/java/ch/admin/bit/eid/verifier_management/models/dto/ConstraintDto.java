package ch.admin.bit.eid.verifier_management.models.dto;

import lombok.Data;

import java.util.List;

@Data
public class ConstraintDto {
    private List<String> path;
    private FilterDto filter;
}
