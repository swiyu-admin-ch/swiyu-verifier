package ch.admin.bj.swiyu.verifier.dto.management;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ResponseModeType", description = "Supported response_mode as defined in OID4VP", enumAsRef = true)
public enum ResponseModeTypeDto {
    DIRECT_POST("direct_post"),
    DIRECT_POST_JWT("direct_post.jwt");

    private final String display;

    ResponseModeTypeDto(String display) {
        this.display = display;
    }

    @JsonValue
    @Override
    public String toString() {
        return this.display;
    }
}
