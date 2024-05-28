package ch.admin.bit.eid.verifier_management.models.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@RedisHash("PresentationDefinition")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// TODO check
public class PresentationDefinition implements Serializable {

    @Id
    private UUID id;

    private List<InputDescriptor> inputDescriptors;

    private ClientMetadata clientMetadata;

    @TimeToLive
    private int expiresAt;
}
