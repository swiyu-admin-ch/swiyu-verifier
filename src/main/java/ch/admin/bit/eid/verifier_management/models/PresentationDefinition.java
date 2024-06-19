package ch.admin.bit.eid.verifier_management.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Data
@RedisHash
@Builder
@AllArgsConstructor
public class PresentationDefinition implements Serializable {

    @Id
    private UUID id;

    private List<InputDescriptor> inputDescriptors;

    // private HashMap<String, Object> submissionRequirements;

    @TimeToLive
    private long expirationInSeconds;
}
