package ch.admin.bit.eid.verifier_management.models;

import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import ch.admin.bit.eid.verifier_management.models.dto.InputDescriptorDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

@Data
@RedisHash
@Builder
@AllArgsConstructor
public class PresentationDefinition implements Serializable {

    @Id
    private String id;

    private String name;

    private String purpose;

    private HashMap<String, FormatAlgorithmDto> format;

    private List<InputDescriptorDto> inputDescriptors;

    @TimeToLive
    private long expirationInSeconds;
}
