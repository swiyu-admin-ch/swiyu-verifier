package ch.admin.bit.eid.oid4vp.model.persistence;

import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.TimeToLive;

import java.util.HashMap;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseData {

    @Id
    private UUID id;

    private ResponseErrorCodeEnum errorCode;

    private String credentialSubjectData;

    @TimeToLive
    private long expirationInSeconds;
}