package ch.admin.bj.swiyu.verifier.dto;

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
     * Maps a union DTO to a standard Presentation Exchange DTO.
     *
     * @param payload union payload
     * @return mapped standard presentation
     * @throws IllegalArgumentException if the payload does not match the expected shape
     */
    public static VerificationPresentationRequestDto toStandardPresentation(VerificationPresentationUnionDto payload) {
        if (payload == null || !payload.isPresentationExchange()) {
            throw new IllegalArgumentException("Union DTO does not contain standard presentation data");
        }
        var dto = new VerificationPresentationRequestDto();
        dto.setVpToken((String) payload.getVp_token());
        dto.setPresentationSubmission(payload.getPresentation_submission());
        return dto;
    }

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
     * This makes the mapper robust against upstream parsing/encoding differences.
     *
     * @param payload union payload
     * @return mapped DCQL request
     * @throws IllegalArgumentException if the payload does not match the expected shape or cannot be parsed
     */
    public static VerificationPresentationDCQLRequestDto toDcqlPresentation(VerificationPresentationUnionDto payload) {
        if (payload == null || !payload.isDcqlPresentation()) {
            throw new IllegalArgumentException("Union DTO does not contain DCQL presentation data");
        }

        var dto = new VerificationPresentationDCQLRequestDto();

        try {
            Map<String, List<String>> mapToken;

            Object vpTokenRaw = payload.getVp_token();

            if (vpTokenRaw instanceof String vpTokenString) {
                JsonNode root = OBJECT_MAPPER.readTree(vpTokenString);

                if (root != null && root.isObject() && root.has("vp_token")) {
                    JsonNode inner = root.get("vp_token");
                    mapToken = OBJECT_MAPPER.convertValue(inner, new TypeReference<>() {
                    });
                } else {
                    mapToken = OBJECT_MAPPER.convertValue(root, new TypeReference<>() {
                    });
                }
            } else {
                mapToken = OBJECT_MAPPER.convertValue(vpTokenRaw, new TypeReference<>() {
                });
            }

            dto.setVpToken(mapToken);
            return dto;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse vp_token as DCQL format: " + e.getMessage(), e);
        }
    }
}
