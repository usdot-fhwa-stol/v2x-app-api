package usdot.v2x.app.api.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Utility class for TIM expiration calculations and J2735 timing conversions.
 * 
 * This class provides static methods for converting J2735 timing fields to Java
 * time objects
 * and calculating expiration times based on TIM message content.
 * 
 * J2735 Timing Fields:
 * - startYear: The year when the TIM becomes valid
 * - startTime: Minutes offset from January 1st of startYear
 * - durationTime: Duration in minutes that the TIM remains valid
 * 
 * Example:
 * - startYear: 2025
 * - startTime: 430738 (minutes into the year)
 * - durationTime: 2 (minutes)
 * 
 * This means the TIM is valid from:
 * - Start: January 1, 2025 + 430738 minutes = March 1, 2025 14:58
 * - End: Start time + 2 minutes = March 1, 2025 15:00
 */
public class TimExpirationUtils {

    /**
     * J2735 maximum value for {@code durationTime} (32000 minutes), meaning the TIM
     * should not expire.
     */
    public static final long NEVER_EXPIRE_DURATION_MINUTES = 32000L;

    /**
     * Returns true when {@code durationTime} is the J2735 maximum (32000 minutes),
     * indicating the TIM should never expire.
     */
    public static boolean isNeverExpiring(long durationTime) {
        return durationTime == NEVER_EXPIRE_DURATION_MINUTES;
    }

    /**
     * Convert J2735 timing fields to a LocalDateTime representing the start time.
     * 
     * @param startYear        The year when the TIM becomes valid
     * @param startTimeMinutes Minutes offset from January 1st of startYear
     * @return The calculated start time
     */
    public static LocalDateTime calculateStartTime(int startYear, long startTimeMinutes) {
        LocalDateTime startOfYear = LocalDateTime.of(startYear, 1, 1, 0, 0);
        return startOfYear.plusMinutes(startTimeMinutes);
    }

    /**
     * Convert J2735 timing fields to a LocalDateTime representing the end time.
     * 
     * @param startYear        The year when the TIM becomes valid
     * @param startTimeMinutes Minutes offset from January 1st of startYear
     * @param durationTime     Duration in minutes that the TIM remains valid
     * @return The calculated end time
     */
    public static LocalDateTime calculateEndTime(int startYear, long startTimeMinutes, long durationTime) {
        LocalDateTime startTime = calculateStartTime(startYear, startTimeMinutes);
        return startTime.plusMinutes(durationTime);
    }

    /**
     * Convert J2735 timing fields to an Instant representing the end time with
     * grace period.
     * 
     * @param startYear        The year when the TIM becomes valid
     * @param startTimeMinutes Minutes offset from January 1st of startYear
     * @param durationTime     Duration in minutes that the TIM remains valid
     * @param gracePeriod      Additional grace period after calculated expiration
     * @return The calculated expiration time as an Instant, or null when
     *         {@code durationTime} is {@link #NEVER_EXPIRE_DURATION_MINUTES}
     */
    public static Instant calculateExpirationTime(int startYear, long startTimeMinutes, long durationTime,
            Duration gracePeriod) {
        if (isNeverExpiring(durationTime)) {
            return null;
        }
        Duration effectiveGracePeriod = gracePeriod != null ? gracePeriod : Duration.ZERO;
        LocalDateTime endTime = calculateEndTime(startYear, startTimeMinutes, durationTime);
        LocalDateTime expirationTime = endTime.plus(effectiveGracePeriod);
        return expirationTime.toInstant(ZoneOffset.UTC);
    }

    /**
     * Check if a TIM is currently valid based on J2735 timing fields.
     * 
     * @param startYear        The year when the TIM becomes valid
     * @param startTimeMinutes Minutes offset from January 1st of startYear
     * @param durationTime     Duration in minutes that the TIM remains valid
     * @return True if the TIM is currently valid, false otherwise
     */
    public static boolean isTimCurrentlyValid(int startYear, long startTimeMinutes, long durationTime) {
        if (isNeverExpiring(durationTime)) {
            return true;
        }
        LocalDateTime endTime = calculateEndTime(startYear, startTimeMinutes, durationTime);
        LocalDateTime currentTime = LocalDateTime.now(ZoneOffset.UTC);
        return endTime.isAfter(currentTime);
    }
}
