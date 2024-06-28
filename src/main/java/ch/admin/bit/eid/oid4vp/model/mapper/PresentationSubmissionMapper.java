package ch.admin.bit.eid.oid4vp.model.mapper;

import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.Base64;

import static org.apache.commons.lang3.StringUtils.isBlank;

@UtilityClass
public class PresentationSubmissionMapper {

    public static String decodeBase64(String base64EncodedString) {
        byte[] decodedBytes = Base64.getUrlDecoder().decode(base64EncodedString);
        return new String(decodedBytes);
    }

    public static PresentationSubmission base64UrlEncodedStringToPresentationSubmission(String base64EncodedString) {
        if (isBlank(base64EncodedString)) {
            return null;
        }

        PresentationSubmission presentationSubmission;
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            presentationSubmission = objectMapper.readValue(decodeBase64(base64EncodedString), PresentationSubmission.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        return presentationSubmission;
    }
}
