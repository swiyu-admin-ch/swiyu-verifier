/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "OID4VP service",
                description = "Generic Verifier Agent OID4VP service",
                contact = @Contact(
                        email = "eid@bit.admin.ch",
                        name = "eID Generica",
                        url = "https://confluence.bit.admin.ch/display/EIDTEAM/E-ID+Team+Tergum"
                )
        )
)
public class OpenApiConfig {
}
