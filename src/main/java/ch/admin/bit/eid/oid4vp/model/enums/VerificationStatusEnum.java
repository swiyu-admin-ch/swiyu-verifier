package ch.admin.bit.eid.oid4vp.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum VerificationStatusEnum {
    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String displayName;
    @JsonValue
    @Override
    public String toString() {
        return this.getDisplayName();
    }
}
