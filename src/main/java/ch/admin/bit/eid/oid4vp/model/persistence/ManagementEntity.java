package ch.admin.bit.eid.oid4vp.model.persistence;

import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.UUID;

@RedisHash("Management")
@Data
@Builder
public class ManagementEntity {

    @Id
    private UUID id;

    private String requestNonce;

    private VerificationStatusEnum state;

    private PresentationDefinition requestedPresentation;

    private ResponseData walletResponse;

    @TimeToLive
    private long expirationInSeconds;
}