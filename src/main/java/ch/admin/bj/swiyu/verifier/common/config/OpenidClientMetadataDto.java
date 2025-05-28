package ch.admin.bj.swiyu.verifier.common.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class OpenidClientMetadataDto {

    @NotNull
    @NotBlank
    @JsonProperty("client_id")
    private String clientId;

    @NotNull
    @JsonProperty("vp_formats")
    private OpenIdClientMetadataVpFormat vpFormats;

    private String version;

    // Dynamic fields
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonAnySetter
    public void setAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}