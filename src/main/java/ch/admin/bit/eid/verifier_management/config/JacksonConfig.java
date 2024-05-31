package ch.admin.bit.eid.verifier_management.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.springframework.context.annotation.Bean;

public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.registerSubtypes(
                new NamedType(VpTokenString.class, "string"),
                new NamedType(VpTokenStringList.class, "list"),
                new NamedType(VpTokenObject.class, "object"),
                new NamedType(VpTokenObjectList.class, "list")
        );

        return objectMapper;
    }
}
