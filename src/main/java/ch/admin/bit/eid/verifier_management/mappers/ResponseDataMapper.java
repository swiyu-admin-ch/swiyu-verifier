package ch.admin.bit.eid.verifier_management.mappers;

import ch.admin.bit.eid.verifier_management.models.ResponseData;
import ch.admin.bit.eid.verifier_management.models.dto.ResponseDataDto;
import lombok.experimental.UtilityClass;

import static ch.admin.bit.eid.verifier_management.utils.MapperUtil.jsonStringToMap;
import static java.util.Objects.nonNull;

@UtilityClass
public class ResponseDataMapper {

    public static ResponseDataDto toDto(ResponseData responseData) {

        if (responseData == null) {
            throw new IllegalArgumentException("Response data cannot be null");
        }

        String credentialSubjectDataString = responseData.getCredentialSubjectData();

        return ResponseDataDto.builder()
                .errorCode(responseData.getErrorCode())
                .errorDescription(responseData.getErrorDescription())
                .credentialSubjectData(nonNull(credentialSubjectDataString) ? jsonStringToMap(credentialSubjectDataString) : null)
                .build();

    }
}
