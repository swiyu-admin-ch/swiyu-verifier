/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Configuration
@Validated
@Data
@ConfigurationProperties(prefix = "application")
public class ApplicationProperties {

    @NotNull
    private Integer verificationTTL;

    @NotNull
    private String externalUrl;

    @NotNull
    private String clientId;

    @NotNull
    private String clientIdScheme;

    @NotNull
    private String signingKey;

    @NotNull
    private String signingKeyVerificationMethod;

    @NotNull
    private String keyManagementMethod;

    private HSMProperties hsm;

    @NotNull
    private String requestObjectVersion = "1.0";

    @NotNull
    private String metadataVersion = "1.0";

    private List<String> acceptedStatusListHosts;
}
