package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.enums.ResponseErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.util.UUID;

@RedisHash
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseData implements Serializable {

    @Id
    private UUID id;

    private ResponseErrorCodeEnum errorCode;

    private String credentialSubjectData;

    @TimeToLive
    private long expirationInSeconds;
}

