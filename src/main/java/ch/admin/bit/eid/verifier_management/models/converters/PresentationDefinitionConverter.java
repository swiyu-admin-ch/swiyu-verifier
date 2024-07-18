package ch.admin.bit.eid.verifier_management.models.converters;

import ch.admin.bit.eid.verifier_management.models.PresentationDefinition;
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
        } catch (JsonProcessingException ex) {
            log.warn("Cannot convert PresentationDefinition to JSON");
            return null;
        }
    }

    @Override
    public PresentationDefinition convertToEntityAttribute(String value) {

        try {
            return objectMapper.readValue(value, PresentationDefinition.class);
        } catch (JsonProcessingException e) {
            log.warn("Cannot convert String to PresentationDefinition");
            return null;
        }
    }
}
