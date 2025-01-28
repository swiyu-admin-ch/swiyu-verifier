package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

@Configuration
@Data
@RequiredArgsConstructor
public class OpenIdClientMetadataConfiguration {
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper objectMapper;

    @NotNull
    @Value("${application.client-metadata-file}")
    private Resource clientMetadataResource;

    private Map<String, Object> openIdClientMetadata;

    @PostConstruct
    public void initOpenIdClientMetadata() throws IOException {
        var template = clientMetadataResource.getContentAsString(Charset.defaultCharset());
        Properties prop = new Properties();
        prop.setProperty("CLIENT_ID", applicationProperties.getClientId());
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");
        var loadedTemplate = helper.replacePlaceholders(template, prop);
        openIdClientMetadata = objectMapper.readValue(loadedTemplate, Map.class);
    }
}
