/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api.definition;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

public class NullOrValidFormatAlgorithmValidator implements ConstraintValidator<NullOrValidFormatAlgorithm, Map<String, FormatAlgorithmDto>> {

    @Override
    public boolean isValid(Map<String, FormatAlgorithmDto> format, ConstraintValidatorContext constraintValidatorContext) {
        if (format == null) {
            return true;
        }

        if (format.isEmpty()) {
            return false;
        }

        return format.entrySet().stream().allMatch(this::isValidSdjwtFormat);
    }

    private boolean isValidSdjwtFormat(Map.Entry<String, FormatAlgorithmDto> entry) {
        final String acceptedSDJWTSFormat = "vc+sd-jwt";

        return acceptedSDJWTSFormat.equals(entry.getKey())
                && entry.getValue().alg() != null && !entry.getValue().alg().isEmpty()
                && entry.getValue().keyBindingAlg() != null;
    }
}
