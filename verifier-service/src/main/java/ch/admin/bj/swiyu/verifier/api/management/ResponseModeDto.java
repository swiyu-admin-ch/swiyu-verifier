package ch.admin.bj.swiyu.verifier.api.management;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ResponseMode", description = "Supported response modes")
public enum ResponseModeDto {
    DIRECT_POST("direct_post"),
    DIRECT_POST_JWT("direct_post.jwt");

    private final String display;

    ResponseModeDto(String display) {
        this.display = display;
    }

    @JsonValue
    @Override
    public String toString() {
        return this.display;
    }
}
