package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import ch.admin.bit.eid.verifier_management.models.AuthorizationResponseData;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class GetVerificationResponseDto  {

    private UUID id;

    private String authorizationRequestObjectUri;

    private UUID authorizationRequestId;

    private VerificationStatusEnum status;

    private AuthorizationResponseData authorizationResponseData;
}
