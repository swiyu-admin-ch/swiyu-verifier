/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */
package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Provides a Jakarta Validator bean via Spring configuration so that services
 * can inject a centrally configured validator instance.
 */
@Configuration
public class VerifierValidationConfiguration {

    @Bean
    public Validator validator() {
        // Integrates with Spring's Bean Validation (Jakarta) infrastructure
        return new LocalValidatorFactoryBean();
    }
}

