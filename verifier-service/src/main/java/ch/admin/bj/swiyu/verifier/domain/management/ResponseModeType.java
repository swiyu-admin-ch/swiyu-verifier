package ch.admin.bj.swiyu.verifier.domain.management;

import com.fasterxml.jackson.annotation.JsonValue;

// JSON-PERSISTED (ZDD): referenced from ResponseSpecification, which is serialized to JSON in the
// "management" table. Don't rename/remove a constant without a migration path for already-stored rows.
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
