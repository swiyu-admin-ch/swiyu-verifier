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

import java.util.regex.Pattern;

public class ValidJsonPathValidator implements ConstraintValidator<ValidJsonPath, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {

        // null values are considered valid
        if (value == null) {
            return true;
        }

        // blank values are considered invalid
        if (value.isBlank()) {
            return false;
        }

        if (hasInvalidConstraintsPath(value)) {
            return false;
        }

        try {
            JsonPath.compile(value);
            return true;
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private boolean hasInvalidConstraintsPath(String value) {
        // JsonPath filter expressions in path are not allowed
        var invalidConstraintPath = Pattern.compile(".*\\[\\s*\\?.*");
        return invalidConstraintPath.matcher(value).matches();
    }
}
