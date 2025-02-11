/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

@Validated
@Data
@Configuration
@ConfigurationProperties(prefix = "application.url-rewrite")
public class UrlRewriteProperties {

    ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() throws JsonProcessingException {
        urlMappings = objectMapper.readValue(mapping, new TypeReference<Map<String, String>>() {
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
