package ch.admin.bit.eid.verifier_management.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@RedisHash("InputDescriptor")
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputDescriptor {

    @Id
    private UUID id;

    private String name;

    private List<String> group;

    private HashMap<String, Object> format;

    private HashMap<String, Object> constraints;
}
