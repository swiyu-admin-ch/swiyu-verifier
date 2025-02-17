/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("monitoring")
@Validated
@Getter
@Setter
public class MonitoringProperties implements Validator {

    @Valid
    BasicAuth basicAuth;

    public boolean supports(Class clazz) {
        return MonitoringProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        var authConfig = (MonitoringProperties) target;
        if (authConfig.getBasicAuth().enabled()) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors,
                    "basic-auth.username", "NotEmpty",
                    "Can't be empty if monitoring.basic-auth.enabled is set.");

            ValidationUtils.rejectIfEmptyOrWhitespace(errors,
                    "basic-auth.password", "NotEmpty",
                    "Can't be empty if monitoring.basic-auth.enabled is set.");
        }
    }

    @Validated
    public record BasicAuth(
            boolean enabled,
            String username,
            String password
    ) {
    }
}
