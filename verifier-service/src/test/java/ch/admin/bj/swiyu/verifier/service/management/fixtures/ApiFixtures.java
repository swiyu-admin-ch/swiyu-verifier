package ch.admin.bj.swiyu.verifier.service.management.fixtures;

import ch.admin.bj.swiyu.verifier.dto.management.CreateVerificationManagementDto;
import ch.admin.bj.swiyu.verifier.dto.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.dto.management.dcql.*;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@UtilityClass
public class ApiFixtures {

    public static CreateVerificationManagementDto createVerificationManagementDto(List<String> acceptedIssuerDids) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(acceptedIssuerDids)
                .jwtSecuredAuthorizationRequest(false)
                .responseMode(ResponseModeTypeDto.DIRECT_POST)
                .build();
    }

    public static CreateVerificationManagementDto createVerificationManagementWithDcqlQueryDto(DcqlQueryDto dcqlQueryDto, List<String> acceptedIssuerDids) {
        return CreateVerificationManagementDto.builder()
                .acceptedIssuerDids(acceptedIssuerDids)
                .jwtSecuredAuthorizationRequest(false)
                .responseMode(ResponseModeTypeDto.DIRECT_POST)
                .dcqlQuery(dcqlQueryDto)
                .build();
    }

    public static CreateVerificationManagementDto createVerificationManagementWithoutResponseMode(List<String> acceptedIssuerDids, DcqlQueryDto dcqlQueryDto) {
        return new CreateVerificationManagementDto(acceptedIssuerDids, null, false, null, null, dcqlQueryDto);
    }

    public static CreateVerificationManagementDto createVerificationManagementDto_Minimal(boolean isJWTSecured) {

        return new CreateVerificationManagementDto(
                List.of("did:example:123"), null,
                isJWTSecured, ResponseModeTypeDto.DIRECT_POST,
                null,
                null
        );
    }

    @NotNull
    public static DcqlQueryDto getDcqlQueryDto() {
        var langs = new ArrayList<>();
        langs.add("languages");
        langs.add(null);

        var claims = List.of(
                new DcqlClaimDto(null, List.of("first_name"), null),
                new DcqlClaimDto(null, List.of("last_name"),null),
                new DcqlClaimDto(null, List.of("birthdate"),null),
                new DcqlClaimDto(null, langs,List.of("FR"))
        );
        return createDcqlQueryDto(claims);
    }

    @NotNull
    public static DcqlQueryDto getDcqlQueryForListDto() {
        List<Object> lang = new ArrayList<>();
        lang.add("languages");
        lang.add(null);

        var claims = List.of(
                new DcqlClaimDto(null, List.of("first_name"), null),
                new DcqlClaimDto(null, List.of("last_name"),null),
                new DcqlClaimDto(null, lang,List.of("FR"))

        );
        return createDcqlQueryDto(claims);
    }

    @NotNull
    public static DcqlQueryDto getDcqlQueryForNestedAddressDto() {
        List<Object> address = new ArrayList<>();
        address.add("company");
        address.add("addresses");
        address.add(null);
        address.add("zip");

        var claims = List.of(
                new DcqlClaimDto(null, address,List.of("8000"))

        );
        return createDcqlQueryDto(claims);
    }

    public static DcqlQueryDto createDcqlQueryDto(List<DcqlClaimDto> claims) {
        // Build a minimal DCQL query DTO
        var meta = new DcqlCredentialMetaDto(
                null,
                List.of("defaultTestVCT"),
                null
        );
        var credential = new DcqlCredentialDto(
                "identity_credential_dcql",
                "dc+sd-jwt",
                null,
                meta,
                claims,
                null,
                true,
                null
        );

        return new DcqlQueryDto(
                List.of(credential),
                List.of()
        );
    }
}