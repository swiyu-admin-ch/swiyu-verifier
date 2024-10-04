package ch.admin.bit.eid.oid4vp.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Utility class for Jackson
 */
public class JacksonUtil {

    /**
     * Convert an object to a map of its properties
     *
     * @param obj the object to convert
     * @return a map of the object's properties
     */
    public static Map<String, Object> getObjectProperties(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(obj, Map.class);
    }

}