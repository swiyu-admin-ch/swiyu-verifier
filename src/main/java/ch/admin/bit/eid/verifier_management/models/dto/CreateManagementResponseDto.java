package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Schema(name = "CreateManagementResponse")
@Getter
@Setter
@Builder
public class CreateManagementResponseDto {

    private UUID id;

    @JsonProperty("request_nonce")
    private String requestNonce;

    private VerificationStatusEnum state;
}
