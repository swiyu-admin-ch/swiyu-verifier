package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ManagementResponseDto {

    private UUID id;

    @JsonProperty("request_nonce")
    private String requestNonce;

    private VerificationStatusEnum state;

    @JsonProperty("presentation_definition")
    private PresentationDefinitionDto presentationDefinition;

    @JsonProperty("wallet_response")
    private ResponseDataDto walletResponse;

    @JsonProperty("verification_url")
    private String verificationUrl;

}
