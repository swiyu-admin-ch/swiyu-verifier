/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.api.VerificationErrorResponseCodeDto;
import ch.admin.bj.swiyu.verifier.api.definition.*;
import ch.admin.bj.swiyu.verifier.api.management.*;
import ch.admin.bj.swiyu.verifier.api.metadata.JwkSetDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.domain.management.*;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.nonNull;
import static org.springframework.util.CollectionUtils.isEmpty;


@UtilityClass
public class ManagementMapper {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ManagementResponseDto toManagementResponseDto(final Management management, final ApplicationProperties props) {
        if (management == null) {
            throw new IllegalArgumentException("Management must not be null");
        }

        var override = management.getConfigurationOverride();
        String externalUrl = StringUtils.isNotEmpty(override.externalUrl()) ? override.externalUrl() : props.getExternalUrl();
        String clientId = StringUtils.isNotEmpty(override.verifierDid()) ? override.verifierDid() : props.getClientId();
        var verificationUrl = String.format("%s/oid4vp/api/request-object/%s", externalUrl, management.getId());
        return new ManagementResponseDto(
                management.getId(),
                management.getRequestNonce(),
                toVerifcationStatusDto(management.getState()),
                toPresentationDefinitionDto(management.getRequestedPresentation()),
                null,
                toResponseDataDto(management.getWalletResponse()),
                verificationUrl,
                buildVerificationDeeplink(verificationUrl, clientId, props.getDeeplinkSchema())
        );
    }

    public static TrustAnchor toTrustAnchor(final TrustAnchorDto trustAnchor) {
        return new TrustAnchor(
                trustAnchor.did(),
                trustAnchor.trustRegistryUri()
        );
    }

    private static String buildVerificationDeeplink(String requestUri, String clientId, String deeplinkSchema) {
        return UriComponentsBuilder.newInstance()
                .scheme(deeplinkSchema)
                .host("")
                .queryParam("client_id", "{p}")
                .queryParam("request_uri", "{q}")
                .build(clientId, requestUri)
                .toString();
    }

    public static PresentationDefinition toPresentationDefinition(PresentationDefinitionDto source) {
        if (source == null) {
            throw new IllegalArgumentException("PresentationDefinitionDto must not be null");
        }
        return new PresentationDefinition(
                source.id(),
                source.name(),
                source.purpose(),
                toFormatAlgorithmMap(source.format()),
                toInputDescriptor(source.inputDescriptors())
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
        return source.stream().map(ManagementMapper::toInputDescriptorDto).toList();
    }

    private static InputDescriptorDto toInputDescriptorDto(PresentationDefinition.InputDescriptor source) {
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
                toFieldDto(source.fields()));
    }

    private static List<FieldDto> toFieldDto(List<Field> source) {
        if (isEmpty(source)) {
            return emptyList();
        }
        return source.stream().map(ManagementMapper::toFieldDto).toList();
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
        if (isEmpty(source)) {
            return emptyMap();
        }
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toFormatAlgorithmDto(entry.getValue())
                ));
    }

    private static FormatAlgorithmDto toFormatAlgorithmDto(FormatAlgorithm source) {
        if (source == null) {
            return null;
        }
        return new FormatAlgorithmDto(toStringList(source.alg()), toStringList(source.keyBindingAlg()));
    }

    private static Map<String, FormatAlgorithm> toFormatAlgorithmMap(Map<String, FormatAlgorithmDto> source) {
        if (isEmpty(source)) {
            return emptyMap();
        }
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toFormatAlgorithm(entry.getValue())
                ));
    }

    private static List<InputDescriptor> toInputDescriptor(List<InputDescriptorDto> source) {
        if (isEmpty(source)) {
            return emptyList();
        }
        return source.stream().map(ManagementMapper::toInputDescriptor).toList();
    }

    private static InputDescriptor toInputDescriptor(InputDescriptorDto source) {
        if (source == null) {
            return null;
        }
        return new InputDescriptor(
                source.id(),
                source.name(),
                source.purpose(),
                toFormatAlgorithmMap(source.format()),
                toConstraint(source.constraints())
        );
    }

    private static Constraint toConstraint(ConstraintDto source) {
        if (source == null) {
            return null;
        }
        return new Constraint(
                source.id(),
                source.name(),
                source.purpose(),
                toFormatAlgorithmMap(source.format()),
                toField(source.fields()));
    }

    private static List<Field> toField(List<FieldDto> source) {
        if (isEmpty(source)) {
            return emptyList();
        }
        return source.stream().map(ManagementMapper::toField).toList();
    }

    private static Field toField(FieldDto source) {
        if (source == null) {
            return null;
        }
        return new Field(toStringList(source.path()), source.id(), source.name(), source.purpose(), toFilter(source.filter()));
    }

    private static Filter toFilter(FilterDto source) {
        if (source == null) {
            return null;
        }
        return new Filter(source.type(), source.constDescriptor());
    }

    private static FormatAlgorithm toFormatAlgorithm(FormatAlgorithmDto source) {
        if (source == null) {
            return null;
        }
        return new FormatAlgorithm(
                toStringList(source.alg()),
                toStringList(source.keyBindingAlg())
        );
    }

    private static List<String> toStringList(List<String> source) {
        return source == null ? emptyList() : new ArrayList<>(source);
    }

    private static Map<String, Object> jsonStringToMap(String jsonString) {
        if (jsonString == null) {
            return new HashMap<>();
        }

        try {
            return OBJECT_MAPPER.readValue(jsonString, Map.class);
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
            case JWT_PREMATURE -> VerificationErrorResponseCodeDto.JWT_PREMATURE;
            case INVALID_FORMAT -> VerificationErrorResponseCodeDto.INVALID_FORMAT;
            case CREDENTIAL_EXPIRED -> VerificationErrorResponseCodeDto.CREDENTIAL_EXPIRED;
            case MISSING_NONCE -> VerificationErrorResponseCodeDto.MISSING_NONCE;
            case UNSUPPORTED_FORMAT -> VerificationErrorResponseCodeDto.UNSUPPORTED_FORMAT;
            case CREDENTIAL_REVOKED -> VerificationErrorResponseCodeDto.CREDENTIAL_REVOKED;
            case CREDENTIAL_SUSPENDED -> VerificationErrorResponseCodeDto.CREDENTIAL_SUSPENDED;
            case HOLDER_BINDING_MISMATCH -> VerificationErrorResponseCodeDto.HOLDER_BINDING_MISMATCH;
            case CREDENTIAL_MISSING_DATA -> VerificationErrorResponseCodeDto.CREDENTIAL_MISSING_DATA;
            case UNRESOLVABLE_STATUS_LIST -> VerificationErrorResponseCodeDto.UNRESOLVABLE_STATUS_LIST;
            case ISSUER_NOT_ACCEPTED -> VerificationErrorResponseCodeDto.ISSUER_NOT_ACCEPTED;
            case PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE ->
                    VerificationErrorResponseCodeDto.PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE;
            case AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND ->
                    VerificationErrorResponseCodeDto.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND;
            case AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM ->
                    VerificationErrorResponseCodeDto.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
            case VERIFICATION_PROCESS_CLOSED -> VerificationErrorResponseCodeDto.VERIFICATION_PROCESS_CLOSED;
            case INVALID_PRESENTATION_DEFINITION -> VerificationErrorResponseCodeDto.INVALID_PRESENTATION_DEFINITION;
            case MALFORMED_CREDENTIAL -> VerificationErrorResponseCodeDto.MALFORMED_CREDENTIAL;
            case PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED ->
                    VerificationErrorResponseCodeDto.PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED;
            case INVALID_PRESENTATION_SUBMISSION -> VerificationErrorResponseCodeDto.INVALID_PRESENTATION_SUBMISSION;
            case INVALID_SCOPE -> VerificationErrorResponseCodeDto.INVALID_SCOPE;
            case INVALID_REQUEST -> VerificationErrorResponseCodeDto.INVALID_REQUEST;
            case INVALID_CLIENT -> VerificationErrorResponseCodeDto.INVALID_CLIENT;
            case VP_FORMATS_NOT_SUPPORTED -> VerificationErrorResponseCodeDto.VP_FORMATS_NOT_SUPPORTED;
            case INVALID_PRESENTATION_DEFINITION_URI ->
                    VerificationErrorResponseCodeDto.INVALID_PRESENTATION_DEFINITION_URI;
            case INVALID_PRESENTATION_DEFINITION_REFERENCE ->
                    VerificationErrorResponseCodeDto.INVALID_PRESENTATION_DEFINITION_REFERENCE;
            case CLIENT_REJECTED -> VerificationErrorResponseCodeDto.CLIENT_REJECTED;
        };
    }

    public static ConfigurationOverride toSigningOverride(@Nullable ConfigurationOverrideDto overrideSigningDto) {
        if (overrideSigningDto == null) {
            return null;
        }
        return new ConfigurationOverride(
                overrideSigningDto.externalUrl(),
                overrideSigningDto.verifierDid(),
                overrideSigningDto.verificationMethod(),
                overrideSigningDto.keyId(),
                overrideSigningDto.keyPin()
        );
    }

    public static @NotNull ResponseModeType toResponseMode(ResponseModeTypeDto responseModeTypeDto) {
        return switch (responseModeTypeDto) {
            case DIRECT_POST -> ResponseModeType.DIRECT_POST;
            case DIRECT_POST_JWT ->  ResponseModeType.DIRECT_POST_JWT;
        };
    }

    public static ResponseModeTypeDto toResponseModeDto(@NotNull ResponseModeType responseModeType) {
        return switch (responseModeType) {
            case DIRECT_POST -> ResponseModeTypeDto.DIRECT_POST;
            case DIRECT_POST_JWT ->  ResponseModeTypeDto.DIRECT_POST_JWT;
        };
    }

    public static JwkSetDto toJWKSetDto(String jwks) {
        try {
            return OBJECT_MAPPER.readValue(jwks, JwkSetDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed Json Web Key Set saved", e);
        }
    }
}