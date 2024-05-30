package ch.admin.bit.eid.oid4vp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
@ConfigurationProperties(prefix = "application")
@Data
public class ApplicationConfiguration {

    @NotNull
    private String externalUrl;

    @NotNull
    private Resource metadataFile;

    private String replaceExternalUri(String template){
        Properties prop = new Properties();
        prop.setProperty("external-url", this.getExternalUrl());
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");
        return helper.replacePlaceholders(template, prop);
    }

    @Cacheable("VerifierMetadata")
    public Map<String, Object> getVerifierMetadata() throws IOException {
        var oidConfJson = metadataFile.getContentAsString(Charset.defaultCharset());
        oidConfJson = replaceExternalUri(oidConfJson);
        return new ObjectMapper().readValue(oidConfJson, HashMap.class);
    }
}
