package ch.admin.bit.eid.verifier_management.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;
import java.util.*;

@RedisHash
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputDescriptor implements Serializable {

    @Id
    private UUID id;

    private String name;

    private List<String> group;

    private String format;

    private String constraints;
}
