package ch.admin.bj.swiyu.verifier.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;

/**
 * Maps a {@link VerificationPresentationUnionDto} into one of the dedicated DTOs used by the verifier.
 * <p>
 * This keeps {@code VerificationPresentationUnionDto} as a pure transport object and centralizes
 * mapping/normalization logic in a single place.
 */
@UtilityClass
public class VerificationPresentationMapper {

    // ObjectMapper is thread-safe for concurrent read/convert operations once fully configured.
    // All configuration must happen here at initialisation; do not mutate this instance afterwards.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Maps a union DTO to a rejection DTO.
     *
     * @param payload union payload
     * @return mapped rejection
     * @throws IllegalArgumentException if the payload does not match the expected shape
     */
    public static VerificationPresentationRejectionDto toRejection(VerificationPresentationUnionDto payload) {
        if (payload == null || !payload.isRejection()) {
            throw new IllegalArgumentException("Union DTO does not contain rejection data");
        }
        var dto = new VerificationPresentationRejectionDto();
        dto.setError(payload.getError());
        dto.setErrorDescription(payload.getError_description());
        return dto;
    }

    /**
     * Maps a union DTO to a DCQL presentation request.
     * <p>
     * The {@code vp_token} field is accepted in multiple shapes:
     * <ul>
     *   <li>Already parsed Java object (e.g., {@code Map&lt;String, List&lt;String&gt;&gt;})</li>
     *   <li>JSON string containing the DCQL map directly</li>
     *   <li>JSON string containing a wrapper object that holds the DCQL map under a top-level "vp_token" key</li>
     * </ul>
     *
     * @param payload union payload
     * @return mapped DCQL request
     * @throws IllegalArgumentException if the payload does not match the expected shape or cannot be parsed
     */
    public static VerificationPresentationDCQLRequestDto toDcqlPresentation(VerificationPresentationUnionDto payload) {
        if (payload == null || !payload.isUnencryptedDcqlPresentation()) {
            throw new IllegalArgumentException("Union DTO does not contain DCQL presentation data");
        }
        try {
            var dto = new VerificationPresentationDCQLRequestDto();
            dto.setVpToken(parseVpToken(payload.getVp_token()));
            return dto;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse vp_token as DCQL format: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the raw {@code vp_token} value into a {@code Map<String, List<String>>}.
     * <p>
     * Handles three input shapes:
     * <ul>
     *   <li>Already a parsed Java {@code Map}</li>
     *   <li>A JSON string representing the DCQL map directly</li>
     *   <li>A JSON string with a wrapper object containing the map under a {@code "vp_token"} key</li>
     * </ul>
     *
     * @param vpTokenRaw raw value from the union DTO
     * @return parsed token map
     * @throws JsonProcessingException if parsing fails
     */
    private static Map<String, List<String>> parseVpToken(Object vpTokenRaw) throws JsonProcessingException {
        if (!(vpTokenRaw instanceof String vpTokenString)) {
            return OBJECT_MAPPER.convertValue(vpTokenRaw, new TypeReference<>() {});
        }

        JsonNode root = OBJECT_MAPPER.readTree(vpTokenString);
        JsonNode tokenNode = (root != null && root.isObject() && root.has("vp_token"))
                ? root.get("vp_token")
                : root;

        return OBJECT_MAPPER.convertValue(tokenNode, new TypeReference<>() {});
    }

}
