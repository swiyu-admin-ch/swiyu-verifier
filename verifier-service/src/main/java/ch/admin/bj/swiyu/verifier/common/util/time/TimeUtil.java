package ch.admin.bj.swiyu.verifier.common.util.time;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;

/**
 * Utility class for time-related operations, with null-safe methods.
 */
@UtilityClass
public class TimeUtil {

    /**
     * Returns the minimum of two values, treating null as "no comparison".
     * @param accumulator The base value (in nanoseconds).
     * @param nullableValue The nullable value to compare (in nanoseconds).
     * @return The smaller of the two values, or accumulator if nullableValue is null.
     */
    public long minWithNullable(long accumulator, @Nullable Long nullableLong) {
        return nullableLong == null ? accumulator : Math.min(accumulator, nullableLong);
    }


    /**
     * Converts seconds to nanoseconds, returning null if input is null.
     * @param nullableIntS Seconds (nullable).
     * @return Nanoseconds, or null.
     */
    public Long secondsToNanos(@Nullable Integer nullableIntS) {
        return nullableIntS == null ? null : TimeUnit.SECONDS.toNanos(nullableIntS);
    }

    /**
     * Converts milliseconds to nanoseconds, returning null if input is null.
     * @param nullableLongMs Milliseconds (nullable).
     * @return Nanoseconds, or null.
     */
    public Long millisToNanos(@Nullable Long nullableLongMs) {
        return nullableLongMs == null ? null : TimeUnit.MILLISECONDS.toNanos(nullableLongMs);
    }


    /**
     * Calculates nanoseconds until expiry from epoch millis.
     * @param expirationTime Epoch in nanoseconds (nullable).
     * @return Nanoseconds until expiry, or null.
     */
    public static Long nanosUntilExpiry(@Nullable Long expirationTime) {
        if (expirationTime == null){
            return null;
        }
        return expirationTime - millisToNanos(Instant.now().toEpochMilli());
    }

    /**
     * Calculates nanoseconds until expiry from Instant.
     * @param expirationTime Instant (nullable).
     * @return Nanoseconds until expiry, or null.
     */
    public static Long nanosUntilExpiry(@Nullable Date expirationTime) {
        if (expirationTime == null){
            return null;
        }
        return nanosUntilExpiry(millisToNanos(expirationTime.getTime()));
    }


    /**
     * Returns the minimum of accumulator and time until expiry.
     * @param accumulator Time in nanoseconds.
     * @param expirationTime Epoch millis (nullable).
     * @return Minimum of accumulator or time until expiry.
     */
    public static long minNanosUntilExpiry(long accumulator, @Nullable Long expirationTime) {
         if (expirationTime == null) {
            return accumulator;
        }
        return minWithNullable(accumulator, nanosUntilExpiry(expirationTime));
    }

    /**
     * Returns the minimum of accumulator and time until expiry.
     * @param accumulator Time in nanoseconds.
     * @param expirationTime Instant (nullable).
     * @return Minimum of accumulator or time until expiry.
     */
    public static long minNanosUntilExpiry(long accumulator, @Nullable Date expirationTime) {
         if (expirationTime == null) {
            return accumulator;
        }
        return minWithNullable(accumulator, nanosUntilExpiry(expirationTime));
    }
}
