package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.ResponseData;
import ch.admin.bit.eid.verifier_management.models.dto.ResponseDataDto;
import lombok.experimental.UtilityClass;

import static ch.admin.bit.eid.verifier_management.utils.MapperUtil.jsonStringToMap;

@UtilityClass
public class ResponseDataMapper {

    public static ResponseDataDto toDto(ResponseData responseData) {

        if (responseData == null) {
            throw new IllegalArgumentException("Response data cannot be null");
        }

        return ResponseDataDto.builder()
                .id(responseData.getId())
                .errorCode(responseData.getErrorCode())
                .credentialSubjectData(jsonStringToMap(responseData.getCredentialSubjectData()))
                .build();

    }
}
