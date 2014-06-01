package com.codeschmoof.android.timelapse.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable, arbitrary-precision signed durations.  This class can be used to represent any
 * {@code <Number, TimeUnit>} pair such as {@code 2 hours}, {@code -30 seconds} or
 * {@code 0.5 nanoseconds}.
 *
 * @author Michael Hixson
 */
public abstract class TimeSpan implements Comparable<TimeSpan>, Serializable {
    // Static factories

    /**
     * Returns a time span of the given {@code duration} in the given {@code unit}.
     */
    public static TimeSpan of(BigDecimal duration, TimeUnit unit) {
        checkNotNull(unit);
        return ofNanos(duration.multiply(nanosPerUnit(unit)));
    }

    /**
     * Returns a time span of the given {@code duration} in the given {@code unit}.
     */
    public static TimeSpan of(long duration, TimeUnit unit) {
        checkNotNull(unit);
        if (duration == 0) {
            return ZeroTimeSpan.INSTANCE;
        }
        if (toNanosFitsInLong(duration, unit)) {
            return new IntegralTimeSpan(unit.toNanos(duration));
        }
        return new DecimalTimeSpan(BigDecimal.valueOf(duration).multiply(nanosPerUnit(unit)));
    }

    /**
     * Parses a time span from its string representation.
     *
     * <p>Time spans are represented by a number followed by a time unit.  A single space can be
     * between the number and unit.  Numbers may be preceeded by a "+" or "-" sign, have zero or more
     * decimal places, and use scientific notation.  Time units must be one of the following:  {@code
     * ns, nsec, nanos, nanosecond, nanoseconds, NANOSECONDS, μs, us, μsec, usec, micros, microsecond,
     * microseconds, MICROSECONDS, ms, msec, millis, millisecond, milliseconds, MILLISECONDS, s, sec,
     * second, seconds, SECONDS, m, min, minute, minutes, MINUTES, h, hr, hour, hours, HOURS, d, day,
     * days, DAYS}.</p>
     *
     * @throws IllegalArgumentException if the input is not parsable
     */
    public static TimeSpan parse(CharSequence input) {
        Matcher matcher = PARSE_PATTERN.matcher(input);
        checkArgument(matcher.matches(), "Invalid time span: %s", input);
        BigDecimal duration = new BigDecimal(matcher.group(1));
        String unitString = matcher.group(3);
        TimeUnit unit = TIME_UNIT_STRINGS.get(unitString);
        checkArgument(unit != null, "Unrecognized time unit: %s", unitString);
        return of(duration, unit);
    }

    /**
     * Returns a time span of zero.
     */
    public static TimeSpan zero() {
        return ZeroTimeSpan.INSTANCE;
    }

    // Constructors

    private TimeSpan() {}

    // Abstract methods

    /**
     * Returns a non-negative time span with the same magnitude as this.
     */
    public abstract TimeSpan abs();

    /**
     * Compares this time span with {@code that}.
     */
    @Override public abstract int compareTo(TimeSpan that);

    /**
     * Returns a time span that is one {@code divisor}<sup>th</sup> the size of this.  The returned
     * value will be rounded down (towards zero) to the nearest nanosecond.
     *
     * @throws ArithmeticException if {@code divisor} is zero
     */
    public abstract TimeSpan dividedBy(BigDecimal divisor);

    /**
     * Returns a time span that is one {@code divisor}<sup>th</sup> the size of this.  The returned
     * value will be rounded to the nearest value at the given {@code scale} using the given
     * {@code roundingMode}.
     *
     * <p>The scale is relative to nanoseconds.  In other words: a scale of zero gives nanosecond
     * precision, a scale of 3 gives picosecond precision, a scale of -3 gives microsecond precision,
     * and so on.</p>
     *
     * @throws ArithmeticException if {@code divisor} is zero or if
     *         {@code roundingMode==RoundingMode.UNNECESSARY} and the given scale is insufficient to
     *         calculate the result
     */
    public abstract TimeSpan dividedBy(BigDecimal divisor, int scale, RoundingMode roundingMode);

    /**
     * Returns a time span that is one {@code divisor}<sup>th</sup> the size of this.  The returned
     * value will be rounded to the nearest nanosecond using the given {@code roundingMode}.
     *
     * @throws ArithmeticException if {@code divisor} is zero or if
     *         {@code roundingMode==RoundingMode.UNNECESSARY} and the result does not represent a
     *         whole number of nanoseconds
     */
    public abstract TimeSpan dividedBy(BigDecimal divisor, RoundingMode roundingMode);

    /**
     * Returns a time span that is one {@code divisor}<sup>th</sup> the size of this.  The returned
     * value will be rounded down (towards zero) to the nearest nanosecond.
     *
     * @throws ArithmeticException if {@code divisor} is zero
     */
    public abstract TimeSpan dividedBy(long divisor);

    /**
     * Returns a time span that is one {@code divisor}<sup>th</sup> the size of this.  The returned
     * value will be rounded to the nearest value at the given {@code scale} using the given
     * {@code roundingMode}.
     *
     * <p>The scale is relative to nanoseconds.  In other words: a scale of zero gives nanosecond
     * precision, a scale of 3 gives picosecond precision, a scale of -3 gives microsecond precision,
     * and so on.</p>
     *
     * @throws ArithmeticException if {@code divisor} is zero or if
     *         {@code roundingMode==RoundingMode.UNNECESSARY} and the given scale is insufficient to
     *         calculate the result
     */
    public abstract TimeSpan dividedBy(long divisor, int scale, RoundingMode roundingMode);

    /**
     * Returns a time span that is one {@code divisor}<sup>th</sup> the size of this.  The returned
     * value will be rounded to the nearest nanosecond using the given {@code roundingMode}.
     *
     * @throws ArithmeticException if {@code divisor} is zero or if
     *         {@code roundingMode==RoundingMode.UNNECESSARY} and the result does not represent a
     *         whole number of nanoseconds
     */
    public abstract TimeSpan dividedBy(long divisor, RoundingMode roundingMode);

    /**
     * Returns {@code true} if the {@code object} is the same time span as this.
     */
    @Override public abstract boolean equals(Object object);

    /**
     * Returns the hash code for this time span.
     */
    @Override public abstract int hashCode();

    /**
     * Returns the difference between this time span and {@code subtrahend}.
     */
    public abstract TimeSpan minus(TimeSpan subtrahend);

    /**
     * Returns the opposite of this time span.
     */
    public abstract TimeSpan negated();

    /**
     * Returns the sum of this time span and {@code addend}.
     */
    public abstract TimeSpan plus(TimeSpan addend);

    /**
     * Returns the signum function of this time span.  (The return value is -1 if this time span is
     * negative; 0 if this time span is zero; and 1 if this time span is positive.)
     */
    public abstract int signum();

    /**
     * Returns a time span that is {@code multiplicand} times the the size of this.
     */
    public abstract TimeSpan times(BigDecimal multiplicand);

    /**
     * Returns a time span that is {@code multiplicand} times the the size of this.
     */
    public abstract TimeSpan times(long multiplicand);

    /**
     * Returns this time span in the given {@code unit}, rounded towards zero.
     *
     * <p>If the true result of this calculation would not fit into a {@code long}, then the closest
     * {@code long} value is returned, i.e. {@link Long#MAX_VALUE} when positive and
     * {@link Long#MIN_VALUE} when negative.</p>
     */
    public abstract long to(TimeUnit unit);

    /**
     * Returns this time span in the given {@code unit} at the given {@code scale} using the given
     * {@code roundingMode}.
     *
     * @throws ArithmeticException if {@code roundingMode==RoundingMode.UNNECESSARY} and the given
     *         scale is insufficient to represent this time span in the given unit
     */
    public abstract BigDecimal to(TimeUnit unit, int scale, RoundingMode roundingMode);

    /**
     * Returns this time span in the given {@code unit} using the given {@code roundingMode}.
     *
     * <p>If the true result of this calculation would not fit into a {@code long}, then the closest
     * {@code long} value is returned, i.e. {@link Long#MAX_VALUE} when positive and
     * {@link Long#MIN_VALUE} when negative.</p>
     *
     * @throws ArithmeticException if {@code roundingMode==RoundingMode.UNNECESSARY} and this time
     *         span is not a whole number in the given unit
     */
    public abstract long to(TimeUnit unit, RoundingMode roundingMode);

    /**
     * Returns this time span in nanoseconds.
     */
    abstract BigDecimal toNanos();

    /**
     * Returns a string representation of this time span, choosing an appropriate unit and using up to
     * four significant figures.  For example, {@code TimeSpan.of(1234567, NANOSECONDS).toString()}
     * returns {@code "1.235 ms"}.
     */
    @Override public abstract String toString();

    // Implementations

    private static final class ZeroTimeSpan extends TimeSpan {
        static final ZeroTimeSpan INSTANCE = new ZeroTimeSpan();

        private ZeroTimeSpan() {}

        @Override public TimeSpan abs() {
            return this;
        }

        @Override public int compareTo(TimeSpan that) {
            return (that == this) ? 0 : -that.signum();
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor) {
            checkDivisor(divisor);
            return this;
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor, int scale, RoundingMode roundingMode) {
            checkNotNull(roundingMode);
            checkDivisor(divisor);
            return this;
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor, RoundingMode roundingMode) {
            checkNotNull(roundingMode);
            checkDivisor(divisor);
            return this;
        }

        @Override public TimeSpan dividedBy(long divisor) {
            checkDivisor(divisor);
            return this;
        }

        @Override public TimeSpan dividedBy(long divisor, int scale, RoundingMode roundingMode) {
            checkNotNull(roundingMode);
            checkDivisor(divisor);
            return this;
        }

        @Override public TimeSpan dividedBy(long divisor, RoundingMode roundingMode) {
            checkNotNull(roundingMode);
            checkDivisor(divisor);
            return this;
        }

        @Override public boolean equals(Object object) {
            return object == this;
        }

        @Override public int hashCode() {
            return 0;
        }

        @Override public TimeSpan minus(TimeSpan subtrahend) {
            return subtrahend.negated();
        }

        @Override public TimeSpan negated() {
            return this;
        }

        @Override public TimeSpan plus(TimeSpan addend) {
            return checkNotNull(addend);
        }

        private Object readResolve() {
            return INSTANCE;
        }

        @Override public int signum() {
            return 0;
        }

        @Override public TimeSpan times(BigDecimal multiplicand) {
            checkNotNull(multiplicand);
            return this;
        }

        @Override public TimeSpan times(long multiplicand) {
            return this;
        }

        @Override public long to(TimeUnit unit) {
            checkNotNull(unit);
            return 0;
        }

        @Override public BigDecimal to(TimeUnit unit, int scale, RoundingMode roundingMode) {
            checkNotNull(unit);
            checkNotNull(roundingMode);
            return BigDecimal.ZERO.setScale(scale);
        }

        @Override public long to(TimeUnit unit, RoundingMode roundingMode) {
            checkNotNull(unit);
            checkNotNull(roundingMode);
            return 0;
        }

        @Override BigDecimal toNanos() {
            return BigDecimal.ZERO;
        }

        @Override public String toString() {
            return "0 ns";
        }

        private static void checkDivisor(long divisor) {
            if (divisor == 0) {
                throw new ArithmeticException("/ by zero");
            }
        }

        private static void checkDivisor(BigDecimal divisor) {
            if (divisor.signum() == 0) {
                throw new ArithmeticException("/ by zero");
            }
        }

        private static final long serialVersionUID = 0;
    }

    private static final class IntegralTimeSpan extends TimeSpan {
        private final long nanos;
        private transient BigDecimal toNanos;
        private transient String toString;

        IntegralTimeSpan(long nanos) {
            this.nanos = nanos;
        }

        @Override public TimeSpan abs() {
            return (nanos > 0) ? this : negated();
        }

        @Override public int compareTo(TimeSpan that) {
            if (that instanceof IntegralTimeSpan) {
                long thatNanos = ((IntegralTimeSpan) that).nanos;
                return (this.nanos < thatNanos) ? -1 : ((this.nanos > thatNanos) ? 1 : 0);
            }
            return this.toNanos().compareTo(that.toNanos());
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor) {
            return ofNanos(toNanos().divide(divisor, 0, RoundingMode.DOWN));
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor, int scale, RoundingMode roundingMode) {
            return ofNanos(toNanos().divide(divisor, scale, roundingMode));
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor, RoundingMode roundingMode) {
            return ofNanos(toNanos().divide(divisor, 0, roundingMode));
        }

        @Override public TimeSpan dividedBy(long divisor) {
            return (divisor == -1) ? negated() : ofNanos(nanos / divisor);
        }

        @Override public TimeSpan dividedBy(long divisor, int scale, RoundingMode roundingMode) {
            return ofNanos(toNanos().divide(BigDecimal.valueOf(divisor), scale, roundingMode));
        }

        @Override public TimeSpan dividedBy(long divisor, RoundingMode roundingMode) {
            return ofNanos(toNanos().divide(BigDecimal.valueOf(divisor), 0, roundingMode));
        }

        @Override public boolean equals(Object object) {
            if (object instanceof IntegralTimeSpan) {
                IntegralTimeSpan that = (IntegralTimeSpan) object;
                return this.nanos == that.nanos;
            }
            return false;
        }

        @Override public int hashCode() {
            return (int) (nanos ^ (nanos >>> 32));
        }

        @Override public TimeSpan minus(TimeSpan subtrahend) {
            if (subtrahend instanceof IntegralTimeSpan) {
                IntegralTimeSpan that = (IntegralTimeSpan) subtrahend;
                if (this.nanos > 0 && that.nanos >= (this.nanos - Long.MAX_VALUE)
                        || this.nanos < 0 && that.nanos <= (this.nanos - Long.MIN_VALUE)) {
                    return ofNanos(this.nanos - that.nanos);
                }
            }
            return ofNanos(toNanos().subtract(subtrahend.toNanos()));
        }

        @Override public TimeSpan negated() {
            if (nanos == Long.MIN_VALUE) {
                return ofNanos(toNanos().negate());
            }
            return ofNanos(-nanos);
        }

        @Override public TimeSpan plus(TimeSpan addend) {
            if (addend instanceof IntegralTimeSpan) {
                IntegralTimeSpan that = (IntegralTimeSpan) addend;
                if (this.nanos > 0 && that.nanos <= (Long.MAX_VALUE - this.nanos)
                        || this.nanos < 0 && that.nanos >= (Long.MIN_VALUE - this.nanos)) {
                    return ofNanos(this.nanos + that.nanos);
                }
            }
            return ofNanos(toNanos().add(addend.toNanos()));
        }

        @Override public int signum() {
            return Long.signum(nanos);
        }

        @Override public TimeSpan times(BigDecimal multiplicand) {
            return ofNanos(toNanos().multiply(multiplicand));
        }

        @Override public TimeSpan times(long multiplicand) {
            if ((!(nanos == Long.MIN_VALUE && multiplicand == -1))
                    && (multiplicand == 0
                    || (nanos <= (Long.MAX_VALUE / multiplicand)
                    && nanos >= (Long.MIN_VALUE / multiplicand)))) {
                return ofNanos(nanos * multiplicand);
            }
            return ofNanos(toNanos().multiply(BigDecimal.valueOf(multiplicand)));
        }

        @Override public long to(TimeUnit unit) {
            return unit.convert(nanos, NANOSECONDS);
        }

        @Override public BigDecimal to(TimeUnit unit, int scale, RoundingMode roundingMode) {
            checkNotNull(unit);
            return toNanos().divide(nanosPerUnit(unit), scale, roundingMode);
        }

        @Override public long to(TimeUnit unit, RoundingMode roundingMode) {
            return saturatedCast(to(unit, 0, roundingMode));
        }

        @Override BigDecimal toNanos() {
            BigDecimal result = toNanos;
            return (result == null) ? (toNanos = BigDecimal.valueOf(nanos)) : result;
        }

        @Override public String toString() {
            String result = toString;
            return (result == null) ? (toString = format(toNanos())) : result;
        }

        private static final long serialVersionUID = 0;
    }

    private static final class DecimalTimeSpan extends TimeSpan {
        private final BigDecimal nanos;
        private transient String toString;

        DecimalTimeSpan(BigDecimal nanos) {
            this.nanos = nanos;
        }

        @Override public TimeSpan abs() {
            return (nanos.signum() > 0) ? this : negated();
        }

        @Override public int compareTo(TimeSpan that) {
            return nanos.compareTo(that.toNanos());
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor) {
            return ofNanos(nanos.divide(divisor, 0, RoundingMode.DOWN));
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor, int scale, RoundingMode roundingMode) {
            return ofNanos(nanos.divide(divisor, scale, roundingMode));
        }

        @Override public TimeSpan dividedBy(BigDecimal divisor, RoundingMode roundingMode) {
            return ofNanos(nanos.divide(divisor, 0, roundingMode));
        }

        @Override public TimeSpan dividedBy(long divisor) {
            return ofNanos(nanos.divide(BigDecimal.valueOf(divisor), 0, RoundingMode.DOWN));
        }

        @Override public TimeSpan dividedBy(long divisor, int scale, RoundingMode roundingMode) {
            return ofNanos(nanos.divide(BigDecimal.valueOf(divisor), scale, roundingMode));
        }

        @Override public TimeSpan dividedBy(long divisor, RoundingMode roundingMode) {
            return ofNanos(nanos.divide(BigDecimal.valueOf(divisor), 0, roundingMode));
        }

        @Override public boolean equals(Object object) {
            if (object instanceof DecimalTimeSpan) {
                DecimalTimeSpan that = (DecimalTimeSpan) object;
                return this.nanos.equals(that.nanos);
            }
            return false;
        }

        @Override public int hashCode() {
            return nanos.hashCode();
        }

        @Override public TimeSpan minus(TimeSpan subtrahend) {
            return ofNanos(nanos.subtract(subtrahend.toNanos()));
        }

        @Override public TimeSpan negated() {
            return ofNanos(nanos.negate());
        }

        @Override public TimeSpan plus(TimeSpan addend) {
            return ofNanos(nanos.add(addend.toNanos()));
        }

        @Override public int signum() {
            return nanos.signum();
        }

        @Override public TimeSpan times(BigDecimal multiplicand) {
            return ofNanos(nanos.multiply(multiplicand));
        }

        @Override public TimeSpan times(long multiplicand) {
            return ofNanos(nanos.multiply(BigDecimal.valueOf(multiplicand)));
        }

        @Override public long to(TimeUnit unit) {
            return to(unit, RoundingMode.DOWN);
        }

        @Override public BigDecimal to(TimeUnit unit, int scale, RoundingMode roundingMode) {
            checkNotNull(unit);
            return nanos.divide(nanosPerUnit(unit), scale, roundingMode);
        }

        @Override public long to(TimeUnit unit, RoundingMode roundingMode) {
            return saturatedCast(to(unit, 0, roundingMode));
        }

        @Override BigDecimal toNanos() {
            return nanos;
        }

        @Override public String toString() {
            String result = toString;
            return (result == null) ? (toString = format(nanos)) : result;
        }

        private static final long serialVersionUID = 0;
    }

    // Miscellaneous private static utilities

    private static final Pattern PARSE_PATTERN =
            Pattern.compile("^([-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?) ?(\\S+)$");

    private static final Map<String, TimeUnit> TIME_UNIT_STRINGS;

    static {
        Map<String, TimeUnit> strings = new HashMap<String, TimeUnit>();
        strings.put("ns", NANOSECONDS);
        strings.put("nsec", NANOSECONDS);
        strings.put("nanos", NANOSECONDS);
        strings.put("nanosecond", NANOSECONDS);
        strings.put("nanoseconds", NANOSECONDS);
        strings.put("NANOSECONDS", NANOSECONDS);
        strings.put("\u03bcs" /* μs */, MICROSECONDS);
        strings.put("us", MICROSECONDS);
        strings.put("\u03bcsec" /* μsec */, MICROSECONDS);
        strings.put("usec", MICROSECONDS);
        strings.put("micros", MICROSECONDS);
        strings.put("microsecond", MICROSECONDS);
        strings.put("microseconds", MICROSECONDS);
        strings.put("MICROSECONDS", MICROSECONDS);
        strings.put("ms", MILLISECONDS);
        strings.put("msec", MILLISECONDS);
        strings.put("millis", MILLISECONDS);
        strings.put("millisecond", MILLISECONDS);
        strings.put("milliseconds", MILLISECONDS);
        strings.put("MILLISECONDS", MILLISECONDS);
        strings.put("s", SECONDS);
        strings.put("sec", SECONDS);
        strings.put("second", SECONDS);
        strings.put("seconds", SECONDS);
        strings.put("SECONDS", SECONDS);
        strings.put("m", MINUTES);
        strings.put("min", MINUTES);
        strings.put("minute", MINUTES);
        strings.put("minutes", MINUTES);
        strings.put("MINUTES", MINUTES);
        strings.put("h", HOURS);
        strings.put("hr", HOURS);
        strings.put("hour", HOURS);
        strings.put("hours", HOURS);
        strings.put("HOURS", HOURS);
        strings.put("d", DAYS);
        strings.put("day", DAYS);
        strings.put("days", DAYS);
        strings.put("DAYS", DAYS);
        TIME_UNIT_STRINGS = Collections.unmodifiableMap(strings);
    }

    private static final BigDecimal MIN_LONG = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE);

    private static final long saturatedCast(BigDecimal value) {
        if (value.compareTo(MIN_LONG) < 0) {
            return Long.MIN_VALUE;
        }
        if (value.compareTo(MAX_LONG) > 0) {
            return Long.MAX_VALUE;
        }
        return value.longValueExact();
    }

    private static TimeSpan ofNanos(BigDecimal nanos) {
        if (nanos.signum() == 0) {
            return ZeroTimeSpan.INSTANCE;
        }
        if (nanos.stripTrailingZeros().scale() <= 0
                && nanos.compareTo(MAX_LONG) <= 0
                && nanos.compareTo(MIN_LONG) >= 0) {
            return new IntegralTimeSpan(nanos.longValueExact());
        }
        return new DecimalTimeSpan(nanos.setScale(Math.max(0, nanos.scale())));
    }

    private static TimeSpan ofNanos(long nanos) {
        if (nanos == 0) {
            return ZeroTimeSpan.INSTANCE;
        }
        return new IntegralTimeSpan(nanos);
    }

    private static final MathContext ROUNDER = new MathContext(4);

    private static String format(BigDecimal nanos) {
        TimeUnit bestUnit = NANOSECONDS;
        BigDecimal absoluteNanos = nanos.abs();
        for (TimeUnit unit : TimeUnit.values()) {
            if (nanosPerUnit(unit).compareTo(absoluteNanos) > 0) {
                break;
            }
            bestUnit = unit;
        }
        return nanos.divide(nanosPerUnit(bestUnit), ROUNDER) + " " + abbreviate(bestUnit);
    }

    private static String abbreviate(TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:
                return "ns";
            case MICROSECONDS:
                return "\u03bcs"; // μs
            case MILLISECONDS:
                return "ms";
            case SECONDS:
                return "s";
            case MINUTES:
                return "m";
            case HOURS:
                return "h";
            case DAYS:
                return "d";
            default:
                throw new AssertionError();
        }
    }

    private static final BigDecimal NANOS_PER_MICRO = BigDecimal.valueOf(MICROSECONDS.toNanos(1));
    private static final BigDecimal NANOS_PER_MILLI = BigDecimal.valueOf(MILLISECONDS.toNanos(1));
    private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(SECONDS.toNanos(1));
    private static final BigDecimal NANOS_PER_MINUTE = BigDecimal.valueOf(MINUTES.toNanos(1));
    private static final BigDecimal NANOS_PER_HOUR = BigDecimal.valueOf(HOURS.toNanos(1));
    private static final BigDecimal NANOS_PER_DAY = BigDecimal.valueOf(DAYS.toNanos(1));

    private static BigDecimal nanosPerUnit(TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:
                return BigDecimal.ONE;
            case MICROSECONDS:
                return NANOS_PER_MICRO;
            case MILLISECONDS:
                return NANOS_PER_MILLI;
            case SECONDS:
                return NANOS_PER_SECOND;
            case MINUTES:
                return NANOS_PER_MINUTE;
            case HOURS:
                return NANOS_PER_HOUR;
            case DAYS:
                return NANOS_PER_DAY;
            default:
                throw new AssertionError();
        }
    }

    private static final long MIN_MICROS = Long.MIN_VALUE / MICROSECONDS.toNanos(1);
    private static final long MAX_MICROS = Long.MAX_VALUE / MICROSECONDS.toNanos(1);
    private static final long MIN_MILLIS = Long.MIN_VALUE / MILLISECONDS.toNanos(1);
    private static final long MAX_MILLIS = Long.MAX_VALUE / MILLISECONDS.toNanos(1);
    private static final long MIN_SECONDS = Long.MIN_VALUE / SECONDS.toNanos(1);
    private static final long MAX_SECONDS = Long.MAX_VALUE / SECONDS.toNanos(1);
    private static final long MIN_MINUTES = Long.MIN_VALUE / MINUTES.toNanos(1);
    private static final long MAX_MINUTES = Long.MAX_VALUE / MINUTES.toNanos(1);
    private static final long MIN_HOURS = Long.MIN_VALUE / HOURS.toNanos(1);
    private static final long MAX_HOURS = Long.MAX_VALUE / HOURS.toNanos(1);
    private static final long MIN_DAYS = Long.MIN_VALUE / DAYS.toNanos(1);
    private static final long MAX_DAYS = Long.MAX_VALUE / DAYS.toNanos(1);

    private static boolean toNanosFitsInLong(long duration, TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:
                return true;
            case MICROSECONDS:
                return duration <= MAX_MICROS && duration >= MIN_MICROS;
            case MILLISECONDS:
                return duration <= MAX_MILLIS && duration >= MIN_MILLIS;
            case SECONDS:
                return duration <= MAX_SECONDS && duration >= MIN_SECONDS;
            case MINUTES:
                return duration <= MAX_MINUTES && duration >= MIN_MINUTES;
            case HOURS:
                return duration <= MAX_HOURS && duration >= MIN_HOURS;
            case DAYS:
                return duration <= MAX_DAYS && duration >= MIN_DAYS;
            default:
                throw new AssertionError();
        }
    }

    private static final long serialVersionUID = 0;
}
