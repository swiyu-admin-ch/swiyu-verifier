package ch.admin.bit.eid.verifier_management.mocks;

import ch.admin.bit.eid.verifier_management.models.dto.FormatAlgorithmDto;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class FormatAlgorithmMocks {

    public static Map<String, FormatAlgorithmDto> createFormatAlgorithmDto() {
        var formats = List.of("EC256");

        return new HashMap<>(Map.of("vc+sd-jwt", FormatAlgorithmDto.builder()
                .alg(formats)
                .keyBindingAlg(formats)
                .build()));
    }
}
