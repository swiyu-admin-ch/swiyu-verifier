/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.management.api.management;

import ch.admin.bj.swiyu.verifier.management.api.definition.PresentationDefinitionDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(name = "CreateVerificationManagement")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateVerificationManagementDto(

        @Valid
        @Schema(description = "List of did from issuer whose credentials are accepted for this verification")
        @JsonProperty("accepted_issuer_dids")
        List<String> acceptedIssuerDids,

        @Schema(description = "Toggle whether the request-object is available as plain object or" +
                "as jwt object signed by the verifier as additional security measure")
        @Valid
        @JsonProperty("jwt_secured_authorization_request")
        Boolean jwtSecuredAuthorizationRequest,

        @Schema(description = "Presentation definition according to " +
                "https://identity.foundation/presentation-exchange/#presentation-definition")
        @Valid
        @NotNull
        @JsonProperty("presentation_definition")
        PresentationDefinitionDto presentationDefinition
) {
}
