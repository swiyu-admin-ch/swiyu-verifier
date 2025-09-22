package ch.admin.bj.swiyu.verifier.domain.management;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResponseModeType {
    DIRECT_POST("direct_post"),
    DIRECT_POST_JWT("direct_post.jwt");

    private final String display;

    ResponseModeType(String display) {
        this.display = display;
    }

    @Override
    @JsonValue
    public String toString() {
        return this.display;
    }
}
