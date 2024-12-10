package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.oid4vp.api.definition.*;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.management.PresentationDefinition;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ch.admin.bj.swiyu.verifier.oid4vp.domain.management.PresentationDefinition.*;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.springframework.util.CollectionUtils.isEmpty;

@UtilityClass
public class RequestObjectMapper {

    public static PresentationDefinitionDto toPresentationDefinitionDto(PresentationDefinition source) {
        if (source == null) {
            return null;
        }
        return PresentationDefinitionDto.builder()
                .id(source.id())
                .name(source.name())
                .purpose(source.purpose())
                .format(toFormatAlgorithmDto(source.format()))
                .inputDescriptors(toInputDescriptorDto(source.inputDescriptors()))
                .build();
    }

    private static List<InputDescriptorDto> toInputDescriptorDto(List<InputDescriptor> source) {
        if (isEmpty(source)) {
            return emptyList();
        }
        return source.stream().map(RequestObjectMapper::toInputDescriptorDto).collect(Collectors.toList());
    }

    private static InputDescriptorDto toInputDescriptorDto(InputDescriptor source) {
        if (source == null) {
            return null;
        }
        return new InputDescriptorDto(
                source.id(),
                source.name(),
                source.purpose(),
                toFormatAlgorithmDto(source.format()),
                toConstraintDto(source.constraints())
        );

    }

    private static ConstraintDto toConstraintDto(Constraint source) {
        if (source == null) {
            return null;
        }
        return new ConstraintDto(
                source.id(),
                source.name(),
                source.purpose(),
                toFormatAlgorithmDto(source.format()),
                toFieldDto(source.fields())
        );
    }

    private static List<FieldDto> toFieldDto(List<Field> source) {
        if (isEmpty(source)) {
            return emptyList();
        }
        return source.stream().map(RequestObjectMapper::toFieldDto).collect(Collectors.toList());
    }

    private static FieldDto toFieldDto(Field source) {
        if (source == null) {
            return null;
        }
        return new FieldDto(
                toStringList(source.path()),
                source.id(),
                source.name(),
                source.purpose(),
                toFilterDto(source.filter())
        );
    }

    private static FilterDto toFilterDto(Filter source) {
        if (source == null) {
            return null;
        }
        return new FilterDto(source.type(), source.constDescriptor());
    }

    private static Map<String, FormatAlgorithmDto> toFormatAlgorithmDto(Map<String, FormatAlgorithm> source) {
        if (isEmpty(source)) {
            return emptyMap();
        }
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        (entry) -> toFormatAlgorithmDto(entry.getValue())
                ));
    }

    private static FormatAlgorithmDto toFormatAlgorithmDto(FormatAlgorithm source) {
        if (source == null) {
            return null;
        }
        return new FormatAlgorithmDto(
                toStringList(source.alg()),
                toStringList(source.keyBindingAlg()),
                toStringList(source.proofType())
        );
    }

    private static List<String> toStringList(List<String> source) {
        return source == null ? emptyList() : new ArrayList<>(source);
    }
}
