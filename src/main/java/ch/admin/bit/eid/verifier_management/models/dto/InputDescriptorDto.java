package ch.admin.bit.eid.verifier_management.models.dto;

import lombok.Data;

/**
 * https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor-object
 */

@Data
public class InputDescriptorDto {

    private String id;

    // TODO check
    private Object format;

    private FieldsDto fields;
}
