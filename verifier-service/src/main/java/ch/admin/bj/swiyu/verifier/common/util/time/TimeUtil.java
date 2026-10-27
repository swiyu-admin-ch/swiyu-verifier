package ch.admin.bj.swiyu.verifier.common.util.time;

import java.time.Clock;
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
     *
     * @param accumulatorNs  The base value (in nanoseconds).
     * @param nullableLongNs The nullable value to compare (in nanoseconds).
     * @return The smaller of the two values, or accumulatorNs if nullableLongNs is null.
     */
    public long minWithNullable(long accumulatorNs, @Nullable Long nullableLongNs) {
        return nullableLongNs == null ? accumulatorNs : Math.min(accumulatorNs, nullableLongNs);
    }


    /**
     * Converts seconds to nanoseconds, returning null if input is null.
     * @param nullableSeconds Seconds (nullable).
     * @return Nanoseconds, or null.
     */
    public Long secondsToNanos(@Nullable Integer nullableSeconds) {
        return nullableSeconds == null ? null : TimeUnit.SECONDS.toNanos(nullableSeconds);
    }

    /**
     * Converts seconds to nanoseconds, returning null if input is null.
     * @param nullableSeconds Seconds (nullable).
     * @return Nanoseconds, or null.
     */
    public Long secondsToNanos(@Nullable Long nullableSeconds) {
        return nullableSeconds == null ? null : TimeUnit.SECONDS.toNanos(nullableSeconds);
    }

    /**
     * Converts milliseconds to nanoseconds, returning null if input is null.
     * @param nullableLongMs Milliseconds (nullable).
     * @return Nanoseconds, or null.
     */
    static Long millisToNanos(@Nullable Long nullableLongMs) {
        return nullableLongMs == null ? null : TimeUnit.MILLISECONDS.toNanos(nullableLongMs);
    }

    /**
     * Calculates nanoseconds until expiry from epoch nanos, based on the system clock.
     * @param expirationTimeNs Epoch in nanoseconds (nullable).
     * @return Nanoseconds until expiry, or null.
     */
    static Long nanosUntilExpiry(@Nullable Long expirationTimeNs) {
        return nanosUntilExpiry(expirationTimeNs, Clock.systemUTC());
    }

    /**
     * Calculates nanoseconds until expiry from epoch nanos.
     * @param expirationTimeNs Epoch in nanoseconds (nullable).
     * @param clock            Clock providing the current time.
     * @return Nanoseconds until expiry (never negative), or null.
     */
    static Long nanosUntilExpiry(@Nullable Long expirationTimeNs, Clock clock) {
        if (expirationTimeNs == null) {
            return null;
        }
        return Math.max(0, expirationTimeNs - millisToNanos(clock.millis()));
    }

    /**
     * Calculates nanoseconds until expiry from a Date.
     * @param expirationTime Date (nullable).
     * @param clock          Clock providing the current time.
     * @return Nanoseconds until expiry (never negative), or null.
     */
    static Long nanosUntilExpiry(@Nullable Date expirationTime, Clock clock) {
        if (expirationTime == null) {
            return null;
        }
        return nanosUntilExpiry(millisToNanos(expirationTime.getTime()), clock);
    }

    /**
     * Returns the minimum of accumulator and time until expiry, based on the system clock.
     * @param accumulatorNs    Time in nanoseconds.
     * @param expirationTimeNs Epoch nanoseconds (nullable).
     * @return Minimum of accumulator or time until expiry.
     */
    public static long minNanosUntilExpiry(long accumulatorNs, @Nullable Long expirationTimeNs) {
        return minNanosUntilExpiry(accumulatorNs, expirationTimeNs, Clock.systemUTC());
    }

    /**
     * Returns the minimum of accumulator and time until expiry.
     * @param accumulatorNs    Time in nanoseconds.
     * @param expirationTimeNs Epoch nanoseconds (nullable).
     * @param clock            Clock providing the current time.
     * @return Minimum of accumulator or time until expiry.
     */
    static long minNanosUntilExpiry(long accumulatorNs, @Nullable Long expirationTimeNs, Clock clock) {
        if (expirationTimeNs == null) {
            return accumulatorNs;
        }
        return minWithNullable(accumulatorNs, nanosUntilExpiry(expirationTimeNs, clock));
    }

    /**
     * Returns the minimum of accumulator and time until expiry, based on the system clock.
     * @param accumulator    Time in nanoseconds.
     * @param expirationTime Date (nullable).
     * @return Minimum of accumulator or time until expiry.
     */
    public static long minNanosUntilExpiry(long accumulator, @Nullable Date expirationTime) {
        return minNanosUntilExpiry(accumulator, expirationTime, Clock.systemUTC());
    }

    /**
     * Returns the minimum of accumulator and time until expiry.
     * @param accumulator    Time in nanoseconds.
     * @param expirationTime Date (nullable).
     * @param clock          Clock providing the current time.
     * @return Minimum of accumulator or time until expiry.
     */
    static long minNanosUntilExpiry(long accumulator, @Nullable Date expirationTime, Clock clock) {
        if (expirationTime == null) {
            return accumulator;
        }
        return minWithNullable(accumulator, nanosUntilExpiry(expirationTime, clock));
    }
}
