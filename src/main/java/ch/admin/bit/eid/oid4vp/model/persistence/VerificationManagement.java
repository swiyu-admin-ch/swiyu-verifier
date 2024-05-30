package ch.admin.bit.eid.oid4vp.model.persistence;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.redis.core.RedisHash;

import java.util.UUID;

@RedisHash("VerificationManagement")
@Data
@Builder
public class VerificationManagement {

    private UUID id;

    private String authorizationRequestId;

    private VerificationStatusEnum status;
}
