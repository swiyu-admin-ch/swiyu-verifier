package ch.admin.bj.swiyu.verifier.management.service;

import ch.admin.bj.swiyu.verifier.management.api.definition.*;
import ch.admin.bj.swiyu.verifier.management.api.management.ManagementResponseDto;
import ch.admin.bj.swiyu.verifier.management.api.management.ResponseDataDto;
import ch.admin.bj.swiyu.verifier.management.api.management.VerificationErrorResponseCodeDto;
import ch.admin.bj.swiyu.verifier.management.api.management.VerificationStatusDto;
import ch.admin.bj.swiyu.verifier.management.domain.management.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ch.admin.bj.swiyu.verifier.management.domain.management.PresentationDefinition.*;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static org.springframework.util.CollectionUtils.isEmpty;


@UtilityClass
public class ManagementMapper {

    public static ManagementResponseDto toManagementResponseDto(final Management management, final String oid4vpUrl) {
        if (management == null) {
            throw new IllegalArgumentException("Management must not be null");
        }

        var verificationUrl = String.format("%s/request-object/%s", oid4vpUrl, management.getId());
        return new ManagementResponseDto(
                management.getId(),
                management.getRequestNonce(),
                toVerifcationStatusDto(management.getState()),
                toPresentationDefinitionDto(management.getRequestedPresentation()),
                toResponseDataDto(management.getWalletResponse()),
                verificationUrl
        );
    }

    public static PresentationDefinition toPresentationDefinition(PresentationDefinitionDto source) {
        if (source == null) {
            throw new IllegalArgumentException("PresentationDefinitionDto must not be null");
        }
        return new PresentationDefinition(
                source.getId(),
                source.getName(),
                source.getPurpose(),
                toFormatAlgorithmMap(source.getFormat()),
                toInputDescriptor(source.getInputDescriptors())
        );
    }

    private static VerificationStatusDto toVerifcationStatusDto(VerificationStatus source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case PENDING -> VerificationStatusDto.PENDING;
            case SUCCESS -> VerificationStatusDto.SUCCESS;
            case FAILED -> VerificationStatusDto.FAILED;
        };
    }

    private static PresentationDefinitionDto toPresentationDefinitionDto(PresentationDefinition source) {
        if (source == null) {
            return null;
        }
        return new PresentationDefinitionDto(
                source.id(),
                source.name(),
                source.purpose(),
                toFormatAlgorithmDtoMap(source.format()),
                toInputDescriptorDto(source.inputDescriptors())
        );
    }

    private static List<InputDescriptorDto> toInputDescriptorDto(List<InputDescriptor> source) {
        if (isEmpty(source)) {
            return emptyList();
        }
        return source.stream().map(ManagementMapper::toInputDescriptorDto).collect(Collectors.toList());
    }

    private static InputDescriptorDto toInputDescriptorDto(InputDescriptor source) {
        if (source == null) {
            return null;
        }
        return new InputDescriptorDto(
                source.id(),
                source.name(),
                source.purpose(),
                toFormatAlgorithmDtoMap(source.format()),
                toConstraintDto(source.constraints())
        );
    }

    private static ResponseDataDto toResponseDataDto(ResponseData source) {
        if (source == null) {
            return null;
        }
        var credentialSubjectDataString = source.credentialSubjectData();
        return new ResponseDataDto(
                toVerificationErrorResponseCodeDto(source.errorCode()),
                source.errorDescription(),
                nonNull(credentialSubjectDataString) ? jsonStringToMap(credentialSubjectDataString) : null);
    }

    private static ConstraintDto toConstraintDto(Constraint source) {
        if (source == null) {
            return null;
        }
        return new ConstraintDto(
                source.id(),
                source.name(),
                source.purpose(),
                toFormatAlgorithmDtoMap(source.format()),
                toFieldDto(source.fields()),
                /* needs clarification: what is limitDisclosure for and how should it be mapped */ null);
    }

    private static List<FieldDto> toFieldDto(List<Field> source) {
        if (source == null) {
            return null;
        }
        return source.stream().map(ManagementMapper::toFieldDto).collect(Collectors.toList());
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

    private static Map<String, FormatAlgorithmDto> toFormatAlgorithmDtoMap(Map<String, FormatAlgorithm> source) {
        if (source == null) {
            return null;
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
        return new FormatAlgorithmDto(toStringList(source.alg()), toStringList(source.keyBindingAlg()));
    }

    private static Map<String, FormatAlgorithm> toFormatAlgorithmMap(Map<String, FormatAlgorithmDto> source) {
        if (source == null) {
            return null;
        }
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        (entry) -> toFormatAlgorithm(entry.getValue())
                ));
    }

    private static List<InputDescriptor> toInputDescriptor(List<InputDescriptorDto> source) {
        if (source == null) {
            return null;
        }
        return source.stream().map(ManagementMapper::toInputDescriptor).collect(Collectors.toList());
    }

    private static InputDescriptor toInputDescriptor(InputDescriptorDto source) {
        if (source == null) {
            return null;
        }
        return new InputDescriptor(
                source.getId(),
                source.getName(),
                source.getPurpose(),
                toFormatAlgorithmMap(source.getFormat()),
                toConstraint(source.getConstraints())
        );
    }

    private static Constraint toConstraint(ConstraintDto source) {
        if (source == null) {
            return null;
        }
        return new Constraint(
                source.getId(),
                source.getName(),
                source.getPurpose(),
                toFormatAlgorithmMap(source.getFormat()),
                toField(source.getFields()));
    }

    private static List<Field> toField(List<FieldDto> source) {
        if (isEmpty(source)) {
            return emptyList();
        }
        return source.stream().map(ManagementMapper::toField).collect(Collectors.toList());
    }

    private static Field toField(FieldDto source) {
        if (source == null) {
            return null;
        }
        return new Field(toStringList(source.getPath()), source.getId(), source.getName(), source.getPurpose(), toFilter(source.getFilter()));
    }

    private static Filter toFilter(FilterDto source) {
        if (source == null) {
            return null;
        }
        return new Filter(source.getType(), source.getConstDescriptor());
    }

    private static FormatAlgorithm toFormatAlgorithm(FormatAlgorithmDto source) {
        if (source == null) {
            return null;
        }
        return new FormatAlgorithm(
                toStringList(source.getAlg()),
                toStringList(source.getKeyBindingAlg())
        );
    }

    private static List<String> toStringList(List<String> source) {
        return source == null ? emptyList() : new ArrayList<>(source);
    }

    private static Map<String, Object> jsonStringToMap(String jsonString) {
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

    private static VerificationErrorResponseCodeDto toVerificationErrorResponseCodeDto(VerificationErrorResponseCode source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case CREDENTIAL_INVALID -> VerificationErrorResponseCodeDto.CREDENTIAL_INVALID;
            case JWT_EXPIRED -> VerificationErrorResponseCodeDto.JWT_EXPIRED;
            case INVALID_FORMAT -> VerificationErrorResponseCodeDto.INVALID_FORMAT;
            case CREDENTIAL_EXPIRED -> VerificationErrorResponseCodeDto.CREDENTIAL_EXPIRED;
            case MISSING_NONCE -> VerificationErrorResponseCodeDto.MISSING_NONCE;
            case UNSUPPORTED_FORMAT -> VerificationErrorResponseCodeDto.UNSUPPORTED_FORMAT;
            case CREDENTIAL_REVOKED -> VerificationErrorResponseCodeDto.CREDENTIAL_REVOKED;
            case CREDENTIAL_SUSPENDED -> VerificationErrorResponseCodeDto.CREDENTIAL_SUSPENDED;
            case HOLDER_BINDING_MISMATCH -> VerificationErrorResponseCodeDto.HOLDER_BINDING_MISMATCH;
            case CREDENTIAL_MISSING_DATA -> VerificationErrorResponseCodeDto.CREDENTIAL_MISSING_DATA;
            case UNRESOLVABLE_STATUS_LIST -> VerificationErrorResponseCodeDto.UNRESOLVABLE_STATUS_LIST;
            case PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE ->
                    VerificationErrorResponseCodeDto.PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE;
            case CLIENT_REJECTED -> VerificationErrorResponseCodeDto.CLIENT_REJECTED;
        };
    }
}
