/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.management;

import ch.admin.bj.swiyu.verifier.api.management.dcql.*;
import ch.admin.bj.swiyu.verifier.domain.management.dcql.*;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Utility class for converting DCQL DTOs to domain objects.
 * Handles the conversion from API DTOs to domain models for DCQL queries.
 */
@UtilityClass
@Slf4j
public class DcqlMapper {

    /**
     * Converts a DcqlQueryDto to DcqlQuery domain object.
     *
     * @param dcqlQueryDto the DTO to convert
     * @return the converted domain object, or null if input is null
     */
    public static DcqlQuery toDcqlQuery(@Nullable DcqlQueryDto dcqlQueryDto) {
        if (dcqlQueryDto == null) {
            return null;
        }

        return DcqlQuery.builder()
                .credentials(dcqlQueryDto.credentials() != null
                        ? dcqlQueryDto.credentials().stream()
                        .map(DcqlMapper::toDcqlCredential)
                        .toList()
                        : null)
                .credentialSets(dcqlQueryDto.credentialSets() != null
                        ? dcqlQueryDto.credentialSets().stream()
                        .map(DcqlMapper::toDcqlCredentialSet)
                        .toList()
                        : null)
                .build();
    }


    public static DcqlQueryDto toDcqlQueryDto(@Nullable DcqlQuery dcqlQuery) {
        if (dcqlQuery == null) {
            return null;
        }

        var credentials = dcqlQuery.getCredentials();
        var credentialSets = dcqlQuery.getCredentialSets();
        return new DcqlQueryDto(
                CollectionUtils.isEmpty(credentials) ? null : credentials.stream().map(DcqlMapper::toDcqlCredentialDto).toList(),
                CollectionUtils.isEmpty(credentialSets) ? null : credentialSets.stream().map(DcqlMapper::toDcqlCredentialSetDto).toList());
    }

    private static DcqlCredentialSetDto toDcqlCredentialSetDto(DcqlCredentialSet credentialSet) {
        return new DcqlCredentialSetDto(credentialSet.getOptions(), credentialSet.getRequired());
    }

    private static DcqlCredentialDto toDcqlCredentialDto(DcqlCredential credential) {
        List<DcqlClaim> claims = credential.getClaims();
        return new DcqlCredentialDto(
                credential.getId(),
                credential.getFormat(),
                credential.getMultiple(),
                DcqlMapper.toDcqlCredentialMetaDto(credential.getMeta()),
                CollectionUtils.isEmpty(claims) ? null : claims.stream().map(DcqlMapper::toDcqlClaimDto).toList(),
                null,
                credential.getRequireCryptographicHolderBinding(),
                null);
    }

    private static DcqlClaimDto toDcqlClaimDto(DcqlClaim dcqlClaim) {
        return new DcqlClaimDto(
                dcqlClaim.getId(),
                dcqlClaim.getPath(),
                dcqlClaim.getValues()
        );
    }

    private static DcqlCredentialMetaDto toDcqlCredentialMetaDto(@NotNull DcqlCredentialMeta meta) {
        return new DcqlCredentialMetaDto(meta.getTypeValues(), meta.getVctValues(), meta.getDoctypeValue());
    }


    /**
     * Converts a DcqlCredentialDto to DcqlCredential domain object.
     *
     * @param dto the DTO to convert
     * @return the converted domain object
     */
    private static DcqlCredential toDcqlCredential(DcqlCredentialDto dto) {
        return DcqlCredential.builder()
                .id(dto.id())
                .format(dto.format())
                .meta(toDcqlCredentialMeta(dto.meta()))
                .claims(dto.claims() != null
                        ? dto.claims().stream()
                        .map(DcqlMapper::toDcqlClaim)
                        .toList()
                        : null)
                .requireCryptographicHolderBinding(dto.requireCryptographicHolderBinding())
                .multiple(dto.multiple())
                .build();
    }

    /**
     * Converts a DcqlCredentialMetaDto to DcqlCredentialMeta domain object.
     *
     * @param dto the DTO to convert
     * @return the converted domain object, or null if input is null
     */
    private static DcqlCredentialMeta toDcqlCredentialMeta(DcqlCredentialMetaDto dto) {
        if (dto == null) {
            return null;
        }

        return DcqlCredentialMeta.builder()
                .vctValues(dto.vctValues())
                .doctypeValue(dto.doctypeValue())
                .typeValues(dto.typeValues() != null
                        ? dto.typeValues().stream()
                        .map(DcqlMapper::ensureNonNullList)
                        .toList()
                        : null)
                .build();
    }


    /**
     * Converts a DcqlClaimDto to DcqlClaim domain object.
     *
     * @param dto the DTO to convert
     * @return the converted domain object
     */
    private static DcqlClaim toDcqlClaim(DcqlClaimDto dto) {

        return DcqlClaim.builder()
                .path(dto.path())
                .id(dto.id())
                .values(dto.values() != null
                        ? dto.values().stream()
                        .toList()
                        : null)
                .build();
    }

    /**
     * Converts a DcqlCredentialSetDto to DcqlCredentialSet domain object.
     *
     * @param dto the DTO to convert
     * @return the converted domain object
     */
    private static DcqlCredentialSet toDcqlCredentialSet(DcqlCredentialSetDto dto) {
        return DcqlCredentialSet.builder()
                .options(dto.options() != null
                        ? dto.options().stream()
                        .map(DcqlMapper::ensureNonNullList)
                        .toList()
                        : null)
                .required(dto.required())
                .build();
    }


    /**
     * Ensures that a list is not null by returning an empty list if input is null.
     *
     * @param list the list to check
     * @return the original list or an empty list if input was null
     */
    private static List<String> ensureNonNullList(List<String> list) {
        return list != null ? list : List.of();
    }
}
