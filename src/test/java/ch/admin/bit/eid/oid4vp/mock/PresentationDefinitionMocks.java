package ch.admin.bit.eid.oid4vp.mock;

import ch.admin.bit.eid.oid4vp.model.dto.Constraint;
import ch.admin.bit.eid.oid4vp.model.dto.Field;
import ch.admin.bit.eid.oid4vp.model.dto.FormatAlgorithm;
import ch.admin.bit.eid.oid4vp.model.dto.InputDescriptor;
import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@UtilityClass
public class PresentationDefinitionMocks {
    public static PresentationDefinition createPresentationDefinitionMock(UUID requestId, List<String> requiredFields) {

        Field field = Field.builder()
                .path(requiredFields)
                .build();

        Constraint constraint = Constraint.builder()
                .fields(List.of(field))
                .build();

        InputDescriptor inputDescriptor = InputDescriptor.builder()
                .id("test_descriptor_id")
                .name("Test Descriptor Name")
                .constraints(List.of(constraint))
                .build();

        HashMap<String, FormatAlgorithm> formats = new HashMap<>();
        formats.put("ldp_vp", FormatAlgorithm.builder()
                .proofType(List.of("BBS2023"))
                .build());

        return PresentationDefinition.builder()
                .id(requestId.toString())
                .inputDescriptors(List.of(inputDescriptor))
                .format(formats)
                .build();
    }
}
