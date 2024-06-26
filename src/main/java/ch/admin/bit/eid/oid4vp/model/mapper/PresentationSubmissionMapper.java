package ch.admin.bit.eid.oid4vp.model.mapper;

import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.Base64;

import static org.apache.commons.lang3.StringUtils.isBlank;

@UtilityClass
public class PresentationSubmissionMapper {

    public static PresentationSubmission base64UrlEncodedStringToPresentationSubmission(String base64EncodedString) {
        if (isBlank(base64EncodedString)) {
            return null;
        }

        PresentationSubmission presentationSubmission;
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(base64EncodedString);
            String decodedString1 = new String(decodedBytes);
            presentationSubmission = objectMapper.readValue(decodedString1, PresentationSubmission.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        return presentationSubmission;
    }
}
