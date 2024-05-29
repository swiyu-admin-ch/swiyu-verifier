package ch.admin.bit.eid.verifier_management.utils;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.LocalDateTime;

@UtilityClass
public class TimeUtils {

    public static long getTTL(int ttl) {
        return Instant.now().plusSeconds(ttl).getEpochSecond();
    }
}
