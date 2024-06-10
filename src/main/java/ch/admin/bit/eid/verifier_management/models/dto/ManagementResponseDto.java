package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagementResponseDto {

    private UUID id;

    private String requestNonce;

    private VerificationStatusEnum state;

    private PresentationDefinitionDto presentationDefinition;

    private ResponseDataDto walletResponse;

    private String verificationUrl;

}
