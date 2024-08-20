package ch.admin.bit.eid.oid4vp.exception;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificationResultNotSupported extends RuntimeException {

    private final transient VerificationError error;
}
