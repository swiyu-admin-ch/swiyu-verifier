package ch.admin.bit.eid.verifier_management.models.entities;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InputDescriptor {

    private String id;

    // TODO check
    private Object format;

    private FieldsModel fields;
}
