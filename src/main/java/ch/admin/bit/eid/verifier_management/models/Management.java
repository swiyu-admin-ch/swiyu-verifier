package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.enums.VerificationStatusEnum;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.util.UUID;

@RedisHash
@Data
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
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
