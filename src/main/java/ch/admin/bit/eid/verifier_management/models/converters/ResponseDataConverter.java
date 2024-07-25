package ch.admin.bit.eid.verifier_management.models.converters;

import ch.admin.bit.eid.verifier_management.models.ResponseData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResponseDataConverter implements AttributeConverter<ResponseData, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ResponseData responseData) {
        try {
            return objectMapper.writeValueAsString(responseData);
        } catch (JsonProcessingException e) {
            log.warn("Cannot convert ResponseData to JSON");
            log.debug(e.toString());
            return null;
        }
    }

    @Override
    public ResponseData convertToEntityAttribute(String value) {
        try {
            return objectMapper.readValue(value, ResponseData.class);
        } catch (JsonProcessingException e) {
            log.warn("Cannot convert JSON to ResponseData");
            log.debug(e.toString());
            return null;
        }
    }
}
