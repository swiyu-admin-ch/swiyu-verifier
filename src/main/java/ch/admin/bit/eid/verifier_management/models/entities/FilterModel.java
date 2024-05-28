package ch.admin.bit.eid.verifier_management.models.entities;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FilterModel {

    private String type;
    private String pattern;
}
