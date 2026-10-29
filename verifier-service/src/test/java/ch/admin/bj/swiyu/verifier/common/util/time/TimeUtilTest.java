package ch.admin.bj.swiyu.verifier.common.util.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.Test;

class TimeUtilTest {

    private static final Instant NOW = Instant.parse("2025-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

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
    void secondsToNanos_int_WithNull_ReturnsNull() {
        Integer i = null;
        assertThat(TimeUtil.secondsToNanos(i)).isNull();
    }
    
    @Test
    void secondsToNanos_long_WithNull_ReturnsNull() {
        Long l = null;
        assertThat(TimeUtil.secondsToNanos(l)).isNull();
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
        assertThat(TimeUtil.nanosUntilExpiry((Long) null, CLOCK)).isNull();
    }

    @Test
    void nanosUntilExpiry_WithExpirationInPast_ReturnsZero() {
        long pastTime = NOW.minusSeconds(10).toEpochMilli();
        assertThat(TimeUtil.nanosUntilExpiry(TimeUtil.millisToNanos(pastTime), CLOCK)).isZero();
    }

    @Test
    void nanosUntilExpiry_WithExpirationInFuture_ReturnsExactDifference() {
        long futureTime = NOW.plusSeconds(10).toEpochMilli();
        assertThat(TimeUtil.nanosUntilExpiry(TimeUtil.millisToNanos(futureTime), CLOCK))
                .isEqualTo(10_000_000_000L);
    }

    @Test
    void nanosUntilExpiry_WithSystemClock_ReturnsPositiveForFuture() {
        long futureTime = Instant.now().plusSeconds(60).toEpochMilli();
        assertThat(TimeUtil.nanosUntilExpiry(TimeUtil.millisToNanos(futureTime))).isPositive();
    }

    // ==================== nanosUntilExpiry (Date) ====================
    @Test
    void nanosUntilExpiry_WithNullDate_ReturnsNull() {
        assertThat(TimeUtil.nanosUntilExpiry((Date) null, CLOCK)).isNull();
    }

    @Test
    void nanosUntilExpiry_WithDateInPast_ReturnsZero() {
        assertThat(TimeUtil.nanosUntilExpiry(Date.from(NOW.minusSeconds(10)), CLOCK)).isZero();
    }

    @Test
    void nanosUntilExpiry_WithDateInFuture_ReturnsExactDifference() {
        assertThat(TimeUtil.nanosUntilExpiry(Date.from(NOW.plusSeconds(10)), CLOCK))
                .isEqualTo(10_000_000_000L);
    }

    // ==================== minNanosUntilExpiry (Long) ====================
    @Test
    void minNanosUntilExpiry_WithNullExpiration_ReturnsAccumulator() {
        assertThat(TimeUtil.minNanosUntilExpiry(1_000_000_000L, (Long) null, CLOCK)).isEqualTo(1_000_000_000L);
    }

    @Test
    void minNanosUntilExpiry_WithExpirationBeforeAccumulator_ReturnsTimeUntilExpiry() {
        long exp = TimeUtil.millisToNanos(NOW.plusSeconds(10).toEpochMilli());
        assertThat(TimeUtil.minNanosUntilExpiry(20_000_000_000L, exp, CLOCK)).isEqualTo(10_000_000_000L);
    }

    @Test
    void minNanosUntilExpiry_WithAccumulatorBeforeExpiration_ReturnsAccumulator() {
        long exp = TimeUtil.millisToNanos(NOW.plusSeconds(10).toEpochMilli());
        assertThat(TimeUtil.minNanosUntilExpiry(5_000_000_000L, exp, CLOCK)).isEqualTo(5_000_000_000L);
    }

    // ==================== minNanosUntilExpiry (Date) ====================
    @Test
    void minNanosUntilExpiry_WithDateNull_ReturnsAccumulator() {
        assertThat(TimeUtil.minNanosUntilExpiry(1_000_000_000L, (Date) null, CLOCK)).isEqualTo(1_000_000_000L);
    }

    @Test
    void minNanosUntilExpiry_WithDateBeforeAccumulator_ReturnsTimeUntilExpiry() {
        assertThat(TimeUtil.minNanosUntilExpiry(20_000_000_000L, Date.from(NOW.plusSeconds(10)), CLOCK))
                .isEqualTo(10_000_000_000L);
    }

    @Test
    void minNanosUntilExpiry_WithAccumulatorBeforeDate_ReturnsAccumulator() {
        assertThat(TimeUtil.minNanosUntilExpiry(5_000_000_000L, Date.from(NOW.plusSeconds(10)), CLOCK))
                .isEqualTo(5_000_000_000L);
    }
}
