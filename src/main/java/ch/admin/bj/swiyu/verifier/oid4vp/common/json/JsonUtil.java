package ch.admin.bj.swiyu.verifier.oid4vp.common.json;

import java.util.Map;
import java.util.stream.Collectors;

public class JsonUtil {
    /**
     * Transforms type-safe transformation of an undefined JSON claim to a Map representing a JSON Object
     *
     * @param untypedMap the claim to transform
     * @return a typed Map
     */
    public static Map<String, Object> getJsonObject(Object untypedMap) {
        if (!(untypedMap instanceof Map)) {
            throw new IllegalArgumentException("Object is not a JSON object");
        }
        return ((Map<?, ?>) untypedMap).entrySet().stream().collect(Collectors.toMap(
                e -> e.getKey().toString(),
                Map.Entry::getValue
        ));
    }
}
