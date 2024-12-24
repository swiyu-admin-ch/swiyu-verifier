package ch.admin.bj.swiyu.verifier.management.api.management;

import ch.admin.bj.swiyu.verifier.management.api.definition.PresentationDefinitionDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Schema(name = "CreateVerificationManagement")
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
