package ch.admin.bj.swiyu.verifier.common.util.time;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

/**
 * Collection of time functions, reducing duplicate code
 */
@UtilityClass
public class TimeUtil {
    public long getMinimum(long accumulator, @Nullable Long nullableLong) {
        if (nullableLong == null) {
            return accumulator;
        }
        return Math.min(accumulator, nullableLong);
    }

    /**
     * Performs a minimum where the accumulator is nanoseconds with a nullable integer in seconds. 
     * If present the integer will be converted to nanoseconds and compared to the accumulator.
     */
    public long getMinimumFromSeconds(long accumulatorNs, @Nullable Integer nullableIntS) {
        if (nullableIntS == null) {
            return accumulatorNs;
        }
        return Math.min(accumulatorNs, TimeUnit.SECONDS.toNanos(nullableIntS.longValue()));
    }

    public long getMinimumExpiry(long accumulatorNs, @Nullable Long expirationTimeMs) {
        if (expirationTimeMs == null) {
            return accumulatorNs;
        }
        long expiryTimeMs = (expirationTimeMs - Instant.now().toEpochMilli());
        return Math.min(accumulatorNs, TimeUnit.MILLISECONDS.toNanos(expiryTimeMs));
    }

    public static long getMinimumExpiry(long accumulatorNs, Date expirationTime) {
         if (expirationTime == null) {
            return accumulatorNs;
        }
        return getMinimumExpiry(accumulatorNs, expirationTime.getTime());
    }
}
