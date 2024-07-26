package ch.admin.bit.eid.oid4vp.model.converter;

import ch.admin.bit.eid.oid4vp.model.persistence.PresentationDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PresentationDefinitionConverter implements AttributeConverter<PresentationDefinition, String> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(PresentationDefinition presentationDefinition) {
        try {
            return objectMapper.writeValueAsString(presentationDefinition);
        } catch (JsonProcessingException e) {
            log.warn("Cannot convert PresentationDefinition to JSON");
            log.debug(e.toString());
            return null;
        }
    }

    @Override
    public PresentationDefinition convertToEntityAttribute(String value) {

        try {
            return objectMapper.readValue(value, PresentationDefinition.class);
        } catch (JsonProcessingException e) {
            log.warn("Cannot convert String to PresentationDefinition");
            log.warn(e.toString());
            return null;
        }
    }
}
