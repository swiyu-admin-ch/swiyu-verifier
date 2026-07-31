package ch.admin.bj.swiyu.verifier.common.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

@Validated
@Data
@ConfigurationProperties(prefix = "application.url-rewrite")
public class UrlRewriteProperties {

    ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws JacksonException {
        urlMappings = objectMapper.readValue(mapping, new TypeReference<>() {
        });
    }

    private String mapping = "{}";
    private Map<String, String> urlMappings = new HashMap<>();

    /**
     * Replace the beginning of the url with the value from the mapping
     *
     * @param url Original url
     * @return Rewritten url
     */
    public String getRewrittenUrl(String url) {
        for (Map.Entry<String, String> entry : urlMappings.entrySet()) {
            if (url.startsWith(entry.getKey())) {
                return url.replace(entry.getKey(), entry.getValue());
            }
        }
        return url;
    }
}
