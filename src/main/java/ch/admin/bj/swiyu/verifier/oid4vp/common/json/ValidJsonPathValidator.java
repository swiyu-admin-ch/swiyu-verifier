/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.json;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidJsonPathValidator implements ConstraintValidator<ValidJsonPath, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {

        if (value == null) {
            return true;
        }

        if (!value.isBlank()) {
            try {
                JsonPath.compile(value);
                return true;
            } catch (InvalidPathException e) {
                return false;
            }
        }

        return false;
    }
}
