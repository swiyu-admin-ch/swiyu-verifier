/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.json;

import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class JsonPathValidator {

    public static boolean isValidJsonPath(String value, ConstraintValidatorContext context) {
        // null values are considered valid
        if (value == null) {
            return true;
        }

        // blank values are considered invalid
        if (value.isBlank()) {
            return false;
        }

        if (containsProhibitedFilteringExpression(value)) {
            setCustomMessage(context, "JsonPath filter expressions are not allowed");
            return false;
        }

        try {
            JsonPath.compile(value);
            return true;
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static boolean containsProhibitedFilteringExpression(String value) {
        // JsonPath filter expressions in path are not allowed
        var invalidConstraintPath = Pattern.compile(".*\\[\\s*\\?.*");
        return invalidConstraintPath.matcher(value).matches();
    }

    private static void setCustomMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
