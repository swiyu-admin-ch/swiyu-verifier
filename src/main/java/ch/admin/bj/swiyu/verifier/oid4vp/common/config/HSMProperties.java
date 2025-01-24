package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

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


    private String securosysConfigIfExists(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return String.format("com.securosys.primus.jce.%s=%s\n", propertyName, value);
    }

    public String securosysStringConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append(securosysConfigIfExists("credentials.host", getHost())); // Primus HSM Host - if used with proxy use proxy host here
        sb.append(securosysConfigIfExists("credentials.port", getPort())); // Primus HSM TCP port - if used with proxy use proxy port here
        sb.append(securosysConfigIfExists("primusProxyUser", getProxyUser())); // Primus Proxy user
        sb.append(securosysConfigIfExists("primusProxyPassword", getProxyPassword())); // Primus Proxy password
        sb.append(securosysConfigIfExists("credentials.user", getUser())); // Primus HSM user
        sb.append(securosysConfigIfExists("credentials.password", getPassword())); // Primus HSM password
        return sb.toString();
    }
}