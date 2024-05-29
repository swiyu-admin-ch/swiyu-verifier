package ch.admin.bit.eid.verifier_management.models.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.UUID;

@RedisHash("VerificationRequestObject")
@Data
@Builder
public class VerificationRequestObject {

    @Id
    private UUID id;

    @JsonProperty("presentation_definition")
    private PresentationDefinition presentationDefinition;

    @JsonProperty("response_uri")
    private String responseUri;

    private String nonce;

    @JsonProperty("response_mode")
    private String responseMode;

    @JsonProperty("client_metadata")
    private ClientMetadata clientMetadata;

    @TimeToLive
    private long expiresAt;
}
