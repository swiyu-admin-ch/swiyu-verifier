package ch.admin.bit.eid.verifier_management.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class MapperUtil {

    public static Map<String, Object> jsonStringToMap(String jsonString) {
        if (jsonString == null) {
            return new HashMap<>();
        }

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return objectMapper.readValue(jsonString, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid string cannot be converted to map");
        }
    }
}
