package ch.admin.bit.eid.oid4vp.model.mapper;

import ch.admin.bit.eid.oid4vp.model.dto.PresentationSubmission;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;

import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isBlank;

@UtilityClass
public class PresentationSubmissionMapper {

    public static PresentationSubmission stringToPresentationSubmission(String presentationSubmissionString) {
        if (isBlank(presentationSubmissionString)) {
            return null;
        }

        PresentationSubmission presentationSubmission;
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        try {
            presentationSubmission = objectMapper.readValue(presentationSubmissionString, PresentationSubmission.class);
            validatePresentationSubmission(presentationSubmission);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid presentation submission");
        }
        
        return presentationSubmission;
    }

    private void validatePresentationSubmission(PresentationSubmission presentationSubmission) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<PresentationSubmission>> violations = validator.validate(presentationSubmission);

            if(violations.isEmpty()) {
                return;
            }

            StringBuilder builder = new StringBuilder();
            for (ConstraintViolation<PresentationSubmission> violation : violations) {
                builder.append("%s%s - %s".formatted(builder.isEmpty() ? "" : ", ", violation.getPropertyPath(), violation.getMessage()));
            }
            throw new IllegalArgumentException("Invalid presentation submission: " + builder);
        }
    }
}
