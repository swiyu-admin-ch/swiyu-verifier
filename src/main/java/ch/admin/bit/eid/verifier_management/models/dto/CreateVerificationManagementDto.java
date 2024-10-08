package ch.admin.bit.eid.verifier_management.models.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateVerificationManagementDto {

    @Valid
    @JsonProperty("jwt_secured_authorization_request")
    private Boolean jwtSecuredAuthorizationRequest;

    @Valid
    @NotNull
    @JsonProperty("presentation_definition")
    private PresentationDefinitionDto presentationDefinition;
}
