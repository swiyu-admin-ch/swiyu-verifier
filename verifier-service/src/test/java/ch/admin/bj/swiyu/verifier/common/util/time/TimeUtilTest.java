package ch.admin.bj.swiyu.verifier.common.util.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

class TimeUtilTest {

    // ==================== minWithNullable ====================
    @Test
    void minWithNullable_WithNullableLongNull_ReturnsAccumulator() {
        long accumulator = 100L;
        assertThat(TimeUtil.minWithNullable(accumulator, null)).isEqualTo(accumulator);
    }

    @Test
    void minWithNullable_WithNullableLongSmaller_ReturnsNullableLong() {
        long accumulator = 100L;
        Long nullableLong = 50L;
        assertThat(TimeUtil.minWithNullable(accumulator, nullableLong)).isEqualTo(nullableLong);
    }

    @Test
    void minWithNullable_WithNullableLongLarger_ReturnsAccumulator() {
        long accumulator = 100L;
        Long nullableLong = 200L;
        assertThat(TimeUtil.minWithNullable(accumulator, nullableLong)).isEqualTo(accumulator);
    }

    // ==================== secondsToNanos ====================
    @Test
    void secondsToNanos_WithNull_ReturnsNull() {
        assertThat(TimeUtil.secondsToNanos(null)).isNull();
    }

    @Test
    void secondsToNanos_WithZero_ReturnsZero() {
        assertThat(TimeUtil.secondsToNanos(0)).isEqualTo(0L);
    }

    @Test
    void secondsToNanos_WithPositiveValue_ReturnsCorrectNanos() {
        assertThat(TimeUtil.secondsToNanos(1)).isEqualTo(1_000_000_000L);
        assertThat(TimeUtil.secondsToNanos(5)).isEqualTo(5_000_000_000L);
    }

    @Test
    void secondsToNanos_WithNegativeValue_ReturnsCorrectNanos() {
        assertThat(TimeUtil.secondsToNanos(-1)).isEqualTo(-1_000_000_000L);
    }

    // ==================== millisToNanos ====================
    @Test
    void millisToNanos_WithNull_ReturnsNull() {
        assertThat(TimeUtil.millisToNanos(null)).isNull();
    }

    @Test
    void millisToNanos_WithZero_ReturnsZero() {
        assertThat(TimeUtil.millisToNanos(0L)).isEqualTo(0L);
    }

    @Test
    void millisToNanos_WithPositiveValue_ReturnsCorrectNanos() {
        assertThat(TimeUtil.millisToNanos(1L)).isEqualTo(1_000_000L);
        assertThat(TimeUtil.millisToNanos(100L)).isEqualTo(100_000_000L);
    }

    @Test
    void millisToNanos_WithNegativeValue_ReturnsCorrectNanos() {
        assertThat(TimeUtil.millisToNanos(-1L)).isEqualTo(-1_000_000L);
    }

    // ==================== nanosUntilExpiry (Long) ====================
    @Test
    void nanosUntilExpiry_WithNullLong_ReturnsNull() {
        assertThat(TimeUtil.nanosUntilExpiry((Long) null)).isNull();
    }

    @Test
    void nanosUntilExpiry_WithExpirationInPast_ReturnsNegative() {
        long pastTime = Instant.now().minusSeconds(10).toEpochMilli();
        Long result = TimeUtil.nanosUntilExpiry(pastTime);
        assertThat(result).isNegative();
    }

    @Test
    void nanosUntilExpiry_WithExpirationInFuture_ReturnsPositive() {
        long futureTime = Instant.now().plusSeconds(10).toEpochMilli();
        Long result = TimeUtil.nanosUntilExpiry(TimeUtil.millisToNanos(futureTime));
        assertThat(result).isPositive();
        assertThat(result).isCloseTo(10_000_000_000L, within(1_000_000L));
    }

    // ==================== nanosUntilExpiry (Date) ====================
    @Test
    void nanosUntilExpiry_WithNullDate_ReturnsNull() {
        assertThat(TimeUtil.nanosUntilExpiry((Date) null)).isNull();
    }

    @Test
    void nanosUntilExpiry_WithDateInPast_ReturnsNegative() {
        Date pastDate = Date.from(Instant.now().minusSeconds(10));
        Long result = TimeUtil.nanosUntilExpiry(pastDate);
        assertThat(result).isNegative();
    }

    @Test
    void nanosUntilExpiry_WithDateInFuture_ReturnsPositive() {
        Date futureDate = Date.from(Instant.now().plusSeconds(10));
        Long result = TimeUtil.nanosUntilExpiry(futureDate);
        assertThat(result).isPositive();
        assertThat(result).isCloseTo(10_000_000_000L, within(1_000_000L));
    }

    // ==================== minNanosUntilExpiry (Long) ====================
    @Test
    void minNanosUntilExpiry_WithNullExpiration_ReturnsAccumulator() {
        long accumulator = 1_000_000_000L;
        Date exp = null;
        assertThat(TimeUtil.minNanosUntilExpiry(accumulator, exp)).isEqualTo(accumulator);
    }

    @Test
    void minNanosUntilExpiry_WithExpirationInFuture_ReturnsMin() {
        long accumulator = 20_000_000_000L;
        long futureTime = Instant.now().plusSeconds(10).toEpochMilli();
        Long nanosUntilExpiry = TimeUtil.nanosUntilExpiry(futureTime);
        long expectedMin = Math.min(accumulator, nanosUntilExpiry);
        assertThat(TimeUtil.minNanosUntilExpiry(accumulator, futureTime))
            .isCloseTo(expectedMin, within(1_000_000L));
    }

    // ==================== minNanosUntilExpiry (Date) ====================
    @Test
    void minNanosUntilExpiry_WithDateNull_ReturnsAccumulator() {
        long accumulator = 1_000_000_000L;
        assertThat(TimeUtil.minNanosUntilExpiry(accumulator, (Date) null)).isEqualTo(accumulator);
    }

    @Test
    void minNanosUntilExpiry_WithDateInFuture_ReturnsMin() {
        long accumulator = 20_000_000_000L;
        Date futureDate = Date.from(Instant.now().plusSeconds(10));
        Long nanosUntilExpiry = TimeUtil.nanosUntilExpiry(futureDate);
        long expectedMin = Math.min(accumulator, nanosUntilExpiry);
        assertThat(TimeUtil.minNanosUntilExpiry(accumulator, futureDate))
            .isCloseTo(expectedMin, within(1_000_000L));
    }
}
