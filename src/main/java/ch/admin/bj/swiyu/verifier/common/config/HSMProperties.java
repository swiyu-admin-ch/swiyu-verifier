/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.common.config;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

@Configuration
@Valid
@Getter
@Setter
public class HSMProperties {
    private String userPin;
    private String keyId;
    private String keyPin;
    private String pkcs11Config;

    private String user;
    private String host;
    private String port;
    private String password;

    private String proxyUser;
    private String proxyPassword;

    public String getSecurosysStringConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSecurosysConfigIfExists("credentials.host", getHost())); // Primus HSM Host - if used with proxy use proxy host here
        sb.append(getSecurosysConfigIfExists("credentials.port", getPort())); // Primus HSM TCP port - if used with proxy use proxy port here
        sb.append(getSecurosysConfigIfExists("primusProxyUser", getProxyUser())); // Primus Proxy user
        sb.append(getSecurosysConfigIfExists("primusProxyPassword", getProxyPassword())); // Primus Proxy password
        sb.append(getSecurosysConfigIfExists("credentials.user", getUser())); // Primus HSM user
        sb.append(getSecurosysConfigIfExists("credentials.password", getPassword())); // Primus HSM password
        return sb.toString();
    }

    private String getSecurosysConfigIfExists(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return String.format("com.securosys.primus.jce.%s=%s\n", propertyName, value);
    }
}
