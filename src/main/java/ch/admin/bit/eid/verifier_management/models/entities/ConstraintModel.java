package ch.admin.bit.eid.verifier_management.models.entities;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConstraintModel {

    private List<String> path;
    private FilterModel filter;
}
