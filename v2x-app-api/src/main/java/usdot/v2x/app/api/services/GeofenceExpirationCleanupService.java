package usdot.v2x.app.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import usdot.v2x.app.api.config.GeofenceProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Scheduled service for automatically deactivating expired Geofence
 * deployments.
 * This service runs at a configurable interval and clears geofences that have
 * passed their expiration time. The interval is configured via
 * {@code geofence.expiration.cleanup.interval}
 * and supports Spring Boot duration format (e.g., "5m", "30s", "1h") or
 * ISO-8601 format.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeofenceExpirationCleanupService {

    private final GeofenceDeploymentService geofenceDeploymentService;
    private final GeofenceProperties geofenceProperties;

    @Scheduled(fixedRateString = "${geofence.expiration.cleanup.interval}")
    @ConditionalOnProperty(name = "geofence.expiration.cleanup.enabled", havingValue = "true", matchIfMissing = true)
    public void cleanupExpiredGeofenceDeployments() {
        try {
            Duration gracePeriod = getGracePeriod();

            log.debug("Starting scheduled cleanup of expired Geofence deployments (grace period: {})",
                    gracePeriod);

            int deactivatedCount = geofenceDeploymentService.deactivateExpiredGeofenceDeployments();

            if (deactivatedCount > 0) {
                log.info(
                        "Scheduled cleanup completed: {} expired Geofence deployments deactivated (grace period: {})",
                        deactivatedCount, gracePeriod);
            } else {
                log.debug("Scheduled cleanup completed: no expired Geofence deployments found (grace period: {})",
                        gracePeriod);
            }

        } catch (Exception e) {
            log.error("Error during scheduled cleanup of expired Geofence deployments", e);
        }
    }

    /**
     * Get the current cleanup interval in minutes.
     * This method is provided for backward compatibility with the REST API.
     * 
     * @return The cleanup interval in minutes
     */
    public int getIntervalMinutes() {
        Duration interval = geofenceProperties.getExpiration().getCleanup().getInterval();
        return interval != null ? (int) interval.toMinutes() : 5;
    }

    /**
     * Get the configured grace period added after TIM end time when setting expiresAt.
     *
     * @return The grace period duration
     */
    public Duration getGracePeriod() {
        Duration gracePeriod = geofenceProperties.getExpiration().getGracePeriod();
        return gracePeriod != null ? gracePeriod : Duration.ofHours(2);
    }

    /**
     * Get the current grace period in hours (truncated).
     * Prefer {@link #getGracePeriod()} for sub-hour values.
     *
     * @return The grace period in whole hours
     */
    public int getGracePeriodHours() {
        return (int) getGracePeriod().toHours();
    }
}
