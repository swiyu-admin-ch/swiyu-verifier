package ch.admin.bit.eid.oid4vp.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor-object
@Getter
@Builder
public class Constraint {

    // optional
    @Valid
    private List<Field> fields;

    // optional
    // TODO add pattern with null
    @JsonProperty("limit_disclosure")
    private String limitDisclosure; // can be required or preferred
}
