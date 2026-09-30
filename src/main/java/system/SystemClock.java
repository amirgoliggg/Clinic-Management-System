package system;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Enterprise Hospital Chronology Engine (24/7 Continuous Operation).
 * Designed for non-stop round-the-clock healthcare environments:
 * 1. Second and millisecond timestamp precision for emergency audit trails.
 * 2. Unrestricted continuous timeline without weekend locks or office-hour constraints.
 * 3. Astronomical Julian Day Number conversion between Persian (Jalali) and Gregorian calendars.
 * 4. Immutable date-time arithmetic for patient telemetry intervals and timeouts.
 * 5. Network time offset drift calibration (NTP synchronization support).
 */
public final class SystemClock implements Serializable, Comparable<SystemClock> {
    private static final long serialVersionUID = 60L;

    private static volatile long networkOffsetMillis = 0L;

    private static final String[] JALALI_MONTH_NAMES = {
            "Farvardin", "Ordibehesht", "Khordad",
            "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar",
            "Dey", "Bahman", "Esfand"
    };

    private static final String[] JALALI_WEEKDAY_NAMES = {
            "Shanbeh", "Yekshanbeh", "Doshanbeh",
            "Seshanbeh", "Chaharshanbeh", "Panjshanbeh", "Jomeh"
    };

    private final long epochMilli;
    private final int jalaliYear;
    private final int jalaliMonth;
    private final int jalaliDay;
    private final int gregorianYear;
    private final int gregorianMonth;
    private final int gregorianDay;
    private final int hour;
    private final int minute;
    private final int second;
    private final int millisecond;

    /**
     * Primary constructor deriving calendar coordinates from hardware epoch milliseconds.
     */
    private SystemClock(long epochMilli) {
        this.epochMilli = epochMilli;

        Instant instant = Instant.ofEpochMilli(epochMilli);
        ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());

        this.gregorianYear = zdt.getYear();
        this.gregorianMonth = zdt.getMonthValue();
        this.gregorianDay = zdt.getDayOfMonth();

        this.hour = zdt.getHour();
        this.minute = zdt.getMinute();
        this.second = zdt.getSecond();
        this.millisecond = zdt.getNano() / 1_000_000;

        int[] jalali = gregorianToJalali(this.gregorianYear, this.gregorianMonth, this.gregorianDay);
        this.jalaliYear = jalali[0];
        this.jalaliMonth = jalali[1];
        this.jalaliDay = jalali[2];
    }

    // =========================================================================
    // STATIC FACTORIES
    // =========================================================================

    /**
     * Captures continuous real-time timestamp applying calibrated network drift.
     */
    public static SystemClock now() {
        long currentSystemMillis = System.currentTimeMillis();
        return new SystemClock(currentSystemMillis + networkOffsetMillis);
    }

    public static SystemClock ofEpochSecond(long epochSecond) {
        return new SystemClock(epochSecond * 1000L);
    }

    public static SystemClock ofEpochMilli(long epochMilli) {
        return new SystemClock(epochMilli);
    }

    public static SystemClock ofGregorian(int year, int month, int day, int hour, int minute, int second) {
        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);
        long millis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new SystemClock(millis);
    }

    public static SystemClock ofJalali(int jYear, int jMonth, int jDay, int hour, int minute, int second) {
        int[] greg = jalaliToGregorian(jYear, jMonth, jDay);
        return ofGregorian(greg[0], greg[1], greg[2], hour, minute, second);
    }

    // =========================================================================
    // NETWORK SYNCHRONIZATION
    // =========================================================================

    public static synchronized void setNetworkTimeOffsetMillis(long offsetMillis) {
        networkOffsetMillis = offsetMillis;
    }

    public static long getNetworkTimeOffsetMillis() {
        return networkOffsetMillis;
    }

    public static synchronized void syncWithNetwork(long officialNetworkEpochMilli) {
        networkOffsetMillis = officialNetworkEpochMilli - System.currentTimeMillis();
    }

    // =========================================================================
    // GETTERS & CONVERSIONS
    // =========================================================================

    public long toEpochMilli() { return epochMilli; }
    public long toEpochSecond() { return epochMilli / 1000L; }

    public int getJalaliYear() { return jalaliYear; }
    public int getJalaliMonth() { return jalaliMonth; }
    public int getJalaliDay() { return jalaliDay; }

    public int getGregorianYear() { return gregorianYear; }
    public int getGregorianMonth() { return gregorianMonth; }
    public int getGregorianDay() { return gregorianDay; }

    public int getHour() { return hour; }
    public int getMinute() { return minute; }
    public int getSecond() { return second; }
    public int getMillisecond() { return millisecond; }

    public String getJalaliMonthName() {
        return JALALI_MONTH_NAMES[jalaliMonth - 1];
    }

    /**
     * Persian day of week: 1 = Shanbeh ... 7 = Jomeh.
     */
    public int getDayOfWeekJalali() {
        Instant instant = Instant.ofEpochMilli(epochMilli);
        int dow = instant.atZone(ZoneId.systemDefault()).getDayOfWeek().getValue();
        return (dow + 1) % 7 + 1;
    }

    public String getDayOfWeekName() {
        return JALALI_WEEKDAY_NAMES[getDayOfWeekJalali() - 1];
    }

    public boolean isJalaliLeapYear() {
        return checkJalaliLeapYear(this.jalaliYear);
    }

    public boolean isGregorianLeapYear() {
        return (gregorianYear % 4 == 0 && gregorianYear % 100 != 0) || (gregorianYear % 400 == 0);
    }

    // =========================================================================
    // IMMUTABLE TIME ARITHMETIC
    // =========================================================================

    public SystemClock plusSeconds(long seconds) {
        return new SystemClock(this.epochMilli + (seconds * 1000L));
    }

    public SystemClock plusMinutes(long minutes) {
        return plusSeconds(minutes * 60L);
    }

    public SystemClock plusHours(long hours) {
        return plusMinutes(hours * 60L);
    }

    public SystemClock plusDays(long days) {
        return plusHours(days * 24L);
    }

    public SystemClock minusSeconds(long seconds) {
        return new SystemClock(this.epochMilli - (seconds * 1000L));
    }

    public SystemClock minusMinutes(long minutes) {
        return minusSeconds(minutes * 60L);
    }

    public SystemClock minusHours(long hours) {
        return minusMinutes(hours * 60L);
    }

    public SystemClock minusDays(long days) {
        return minusHours(days * 24L);
    }

    public long millisBetween(SystemClock other) {
        Objects.requireNonNull(other, "Comparison clock cannot be null.");
        return Math.abs(this.epochMilli - other.epochMilli);
    }

    public long secondsBetween(SystemClock other) {
        return millisBetween(other) / 1000L;
    }

    public long minutesBetween(SystemClock other) {
        return secondsBetween(other) / 60L;
    }

    public boolean isBefore(SystemClock other) {
        Objects.requireNonNull(other, "Comparison clock cannot be null.");
        return this.epochMilli < other.epochMilli;
    }

    public boolean isAfter(SystemClock other) {
        Objects.requireNonNull(other, "Comparison clock cannot be null.");
        return this.epochMilli > other.epochMilli;
    }

    public boolean isSameInstant(SystemClock other) {
        return other != null && this.epochMilli == other.epochMilli;
    }

    // =========================================================================
    // FORMATTING METHODS FOR GUI & LOGS
    // =========================================================================

    public String toDateString() {
        return String.format("%04d/%02d/%02d", jalaliYear, jalaliMonth, jalaliDay);
    }

    public String toTimeString() {
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    public String toFullDisplayString() {
        return toDateString() + " " + toTimeString();
    }

    public String toGregorianDateString() {
        return String.format("%04d-%02d-%02d", gregorianYear, gregorianMonth, gregorianDay);
    }

    public String toAuditString() {
        return String.format("%s (Gregorian: %s) [%s]", toFullDisplayString(), toGregorianDateString(), getDayOfWeekName());
    }

    // =========================================================================
    // ASTRONOMICAL JULIAN DAY NUMBER CONVERSIONS
    // =========================================================================

    private static int[] gregorianToJalali(int gy, int gm, int gd) {
        int[] gDaysInMonths = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int[] jDaysInMonths = {0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};

        int gy2 = (gm > 2) ? (gy + 1) : gy;
        int gDayNo = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80;

        for (int i = 1; i < gm; ++i) {
            gDayNo += gDaysInMonths[i];
        }
        gDayNo += gd;

        int jDayNo = gDayNo - 79;
        int jNp = jDayNo / 12053;
        jDayNo %= 12053;

        int jy = 979 + 33 * jNp + 4 * (jDayNo / 1461);
        jDayNo %= 1461;

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365;
            jDayNo = (jDayNo - 1) % 365;
        }

        int jm = 0;
        int jd = 0;
        for (int i = 1; i <= 12; ++i) {
            if (jDayNo < jDaysInMonths[i]) {
                jm = i;
                jd = jDayNo + 1;
                break;
            }
            jDayNo -= jDaysInMonths[i];
        }

        return new int[]{jy, jm, jd};
    }

    private static int[] jalaliToGregorian(int jy, int jm, int jd) {
        int jDayNo = 365 * (jy - 979) + (jy / 33) * 8 + ((jy % 33 + 3) / 4);
        int[] jDaysInMonths = {0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29};
        for (int i = 1; i < jm; ++i) {
            jDayNo += jDaysInMonths[i];
        }
        jDayNo += jd - 1;

        int gDayNo = jDayNo + 79;
        int gy = 1600 + 400 * (gDayNo / 146097);
        gDayNo %= 146097;

        boolean leap = true;
        if (gDayNo >= 36525) {
            gDayNo--;
            gy += 100 * (gDayNo / 36524);
            gDayNo %= 36524;
            if (gDayNo >= 365) {
                gDayNo++;
            } else {
                leap = false;
            }
        }

        gy += 4 * (gDayNo / 1461);
        gDayNo %= 1461;

        if (gDayNo >= 366) {
            leap = false;
            gDayNo--;
            gy += gDayNo / 365;
            gDayNo %= 365;
        }

        int[] gDaysInMonths = {0, 31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int gm = 0;
        int gd = 0;
        for (int i = 1; i <= 12; ++i) {
            if (gDayNo < gDaysInMonths[i]) {
                gm = i;
                gd = gDayNo + 1;
                break;
            }
            gDayNo -= gDaysInMonths[i];
        }

        return new int[]{gy, gm, gd};
    }

    private static boolean checkJalaliLeapYear(int jy) {
        int[] breaks = {-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178};
        int jp = breaks[0];
        int jm;
        int jump;
        int leapJ = -14;

        if (jy < jp || jy >= breaks[breaks.length - 1]) {
            throw new IllegalArgumentException("Invalid Jalali year for leap calculation: " + jy);
        }

        for (int i = 1; i < breaks.length; i++) {
            jm = breaks[i];
            jump = jm - jp;
            if (jy < jm) {
                int n = jy - jp;
                leapJ += (n / 33) * 8 + ((n % 33 + 3) / 4);
                if ((jump % 33 == 4) && (jump - n == 4)) {
                    leapJ++;
                }
                int leapG = (int) Math.floor((jy + 621) * 0.24219858156) - (int) Math.floor((jy + 621) * 0.24219858156 - 0.5);
                return (leapJ % 33) == 0;
            }
            leapJ += (jump / 33) * 8 + ((jump % 33 + 3) / 4);
            jp = jm;
        }
        return false;
    }

    // =========================================================================
    // STANDARD CONTRACTS
    // =========================================================================

    @Override
    public int compareTo(SystemClock other) {
        Objects.requireNonNull(other, "Cannot compare to a null SystemClock.");
        return Long.compare(this.epochMilli, other.epochMilli);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SystemClock that = (SystemClock) o;
        return epochMilli == that.epochMilli;
    }

    @Override
    public int hashCode() {
        return Objects.hash(epochMilli);
    }

    @Override
    public String toString() {
        return toFullDisplayString();
    }
}