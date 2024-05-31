package ch.admin.bit.eid.verifier_management.models;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.TimeToLive;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NotEmpty
public class PresentationDefinition {

    @Id
    private UUID id;

    private List<InputDescriptor> inputDescriptors;

    private HashMap<String, Object> submissionRequirements;

    @TimeToLive
    private long expirationInSeconds;// **
}
