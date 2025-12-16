/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */
package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.submission.PresentationSubmissionDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;

import static ch.admin.bj.swiyu.verifier.common.exception.VerificationException.submissionError;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Parses and validates a PresentationSubmission JSON using the configured ObjectMapper and Validator.
 */
@Component
@Deprecated(since="OID4VP 1.0")
public class PresentationSubmissionService {

    private final Validator validator;
    // Dedicated mapper that tolerates single quotes in JSON input and is independent from the shared ObjectMapper configuration
    private final ObjectMapper singleQuoteTolerantMapper;

    public PresentationSubmissionService(Validator validator) {
        this.validator = validator;
        this.singleQuoteTolerantMapper = new ObjectMapper()
            .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    }

    /**
     * Parses the given JSON into PresentationSubmissionDto and validates it.
     * Returns null if the JSON string is blank.
     */
    public PresentationSubmissionDto parseAndValidate(String presentationSubmissionJson) {
        if (isBlank(presentationSubmissionJson)) {
            return null;
        }

        try {
            var dto = singleQuoteTolerantMapper.readValue(presentationSubmissionJson, PresentationSubmissionDto.class);
            validate(dto);
            return dto;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw submissionError(e, VerificationErrorResponseCode.INVALID_PRESENTATION_SUBMISSION, e.getMessage());
        }
    }

    /**
     * Validates the PresentationSubmission and throws IllegalArgumentException on constraint violations.
     */
    public void validate(PresentationSubmissionDto presentationSubmission) {
        Set<ConstraintViolation<PresentationSubmissionDto>> violations = validator.validate(presentationSubmission);
        if (violations.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (ConstraintViolation<PresentationSubmissionDto> violation : violations) {
            builder.append("%s%s - %s".formatted(builder.isEmpty() ? "" : ", ", violation.getPropertyPath(), violation.getMessage()));
        }
        throw new IllegalArgumentException("Invalid presentation submission: " + builder);
    }
}
