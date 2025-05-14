/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.json;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class ValidJsonPathListValidator implements ConstraintValidator<ValidJsonPath, List<String>> {

    @Override
    public boolean isValid(List<String> values, ConstraintValidatorContext context) {
        if (values == null) {
            return true;
        }

        if (values.isEmpty()) {
            return false;
        }

        for (String value : values) {
            if (!JsonPathValidator.isValidJsonPath(value, context)) {
                return false;
            }
        }
        return true;
    }
}
