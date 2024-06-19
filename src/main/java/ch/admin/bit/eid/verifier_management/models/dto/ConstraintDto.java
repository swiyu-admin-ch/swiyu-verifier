package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

// https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor-object
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConstraintDto {

    // todo check if not null must be empty list
    private List<@Valid FieldDto> fields;

    // TODO add pattern with null
    @JsonProperty("limit_disclosure")
    private String limitDisclosure; // can be required or preferred
}
