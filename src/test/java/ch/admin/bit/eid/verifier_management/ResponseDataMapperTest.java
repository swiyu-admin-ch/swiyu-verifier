package ch.admin.bit.eid.verifier_management;

import ch.admin.bit.eid.verifier_management.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.verifier_management.mappers.ResponseDataMapper;
import ch.admin.bit.eid.verifier_management.models.ResponseData;
import ch.admin.bit.eid.verifier_management.models.dto.ResponseDataDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResponseDataMapperTest {

    @Test
    void toDto_ShouldThrowException_WhenResponseDataIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ResponseDataMapper.toDto(null);
        });

        assertEquals("Response data cannot be null", exception.getMessage());
    }

    @Test
    void toDto_ShouldMapFieldsCorrectly_WhenResponseDataIsValid() {
        ResponseData responseData = new ResponseData();
        responseData.setErrorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID);
        responseData.setErrorDescription("Not Found");
        responseData.setCredentialSubjectData("{\"key\":\"value\"}");

        ResponseDataDto responseDataDto = ResponseDataMapper.toDto(responseData);

        assertNotNull(responseDataDto);
        assertEquals(ResponseErrorCodeEnum.CREDENTIAL_INVALID, responseDataDto.getErrorCode());
        assertEquals("Not Found", responseDataDto.getErrorDescription());
        assertNotNull(responseDataDto.getCredentialSubjectData());
        assertEquals("value", responseDataDto.getCredentialSubjectData().get("key"));
    }

    @Test
    void toDto_ShouldHandleNullCredentialSubjectData() {
        ResponseData responseData = new ResponseData();
        responseData.setErrorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID);
        responseData.setErrorDescription("Not Found");
        responseData.setCredentialSubjectData(null);

        ResponseDataDto responseDataDto = ResponseDataMapper.toDto(responseData);

        assertNotNull(responseDataDto);
        assertEquals(ResponseErrorCodeEnum.CREDENTIAL_INVALID, responseDataDto.getErrorCode());
        assertEquals("Not Found", responseDataDto.getErrorDescription());
        assertNull(responseDataDto.getCredentialSubjectData());
    }
}
