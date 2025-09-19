/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api.management;

import ch.admin.bj.swiyu.verifier.api.definition.PresentationDefinitionDto;
import ch.admin.bj.swiyu.verifier.api.management.dcql.DcqlQueryDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;

import java.util.List;

@Schema(name = "CreateVerificationManagement")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateVerificationManagementDto(

        @Valid
        @Nullable
        @Schema(description = """
                List of dids from issuers whose credentials are accepted for this verification.
                Will be evaluated before trust anchor.
                If not specified and no trust anchor specified all dids are trusted.
                """, example = """
                ["did:example:12345"]
                """)
        @JsonProperty("accepted_issuer_dids")
        List<String> acceptedIssuerDids,

        @Valid
        @Nullable
        @Schema(description = """
                List of trust anchor dids from the trust registry.
                This is an alternative to specifying accepted issuer dids,
                if these dids have a trust statement.
                All dids trusted by the trust anchor are accepted.
                If not specified, trust statements will not be used for this verification.
                """)
        @JsonProperty("trust_anchors")
        List<TrustAnchorDto> trustAnchors,

        @Schema(description = "Toggle whether the request-object is available as plain object or" +
                "as jwt object signed by the verifier as additional security measure")
        @Valid
        @JsonProperty("jwt_secured_authorization_request")
        Boolean jwtSecuredAuthorizationRequest,

        @Schema(description = "Requested Response Mode")
        @JsonProperty(value = "response_mode", defaultValue = "direct_post")
        // TODO Once the Ecosystem broadly supports JWE, we should use direct_post_jwt as default value
        ResponseModeDto responseMode,

        @Schema(description = "Presentation definition according to " +
                "https://identity.foundation/presentation-exchange/#presentation-definition")
        @Valid
        @Nullable
        @JsonProperty("presentation_definition")
        PresentationDefinitionDto presentationDefinition,

        @Schema(description = "Optional Parameter to override configured parameters, such as the DID used or the HSM key used in singing the request object",
                example = """
                {}
                """)
        @Valid
        @Nullable
        @JsonProperty("configuration_override")
        ConfigurationOverrideDto configuration_override,

        @Valid
        @Nullable
        @JsonProperty("dcql_query")
        DcqlQueryDto dcqlQuery
) {
}
