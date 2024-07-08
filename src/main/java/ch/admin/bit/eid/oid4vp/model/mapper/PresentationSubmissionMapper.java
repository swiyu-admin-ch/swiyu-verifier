package ch.admin.bit.eid.oid4vp.model.mapper;

import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import static org.apache.commons.lang3.StringUtils.isBlank;

@UtilityClass
public class PresentationSubmissionMapper {

    public static PresentationSubmission stringToPresentationSubmission(String presentationSubmissionString) {
        if (isBlank(presentationSubmissionString)) {
            return null;
        }

        PresentationSubmission presentationSubmission;
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            presentationSubmission = objectMapper.readValue(presentationSubmissionString, PresentationSubmission.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        return presentationSubmission;
    }
}
