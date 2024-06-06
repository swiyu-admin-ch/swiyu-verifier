package ch.admin.bit.eid.verifier_management.models.dto;

import ch.admin.bit.eid.verifier_management.enums.ResponseErrorCodeEnum;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Builder
@Getter
@Setter
public class ResponseDataDto {

    private UUID id;

    private ResponseErrorCodeEnum errorCode;

    private Map<String, Object> credentialSubjectData;
}
