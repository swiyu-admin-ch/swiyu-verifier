package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.util.UUID;

@RedisHash("Management")
@Data
@Builder
public class Management implements Serializable {

    @Id
    private UUID id;

    private String requestNonce;

    private VerificationStatusEnum state;

    private PresentationDefinition requestedPresentation;

    private ResponseData walletResponse;

    @TimeToLive
    private long expirationInSeconds;
}
