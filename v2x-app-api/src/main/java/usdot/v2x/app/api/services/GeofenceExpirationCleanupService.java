package usdot.v2x.app.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service for automatically deactivating expired Geofence
 * deployments.
 * This service runs at configurable intervals to check for and deactivate
 * Geofence deployments that have passed their expiration time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "geofence.expiration.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class GeofenceExpirationCleanupService {

    private final GeofenceDeploymentService geofenceDeploymentService;

    @Value("${geofence.expiration.cleanup.interval-minutes:5}")
    private int intervalMinutes;

    @Value("${geofence.expiration.grace-period-hours:2}")
    private int gracePeriodHours;

    /**
     * Scheduled method to deactivate expired Geofence deployments.
     * Runs every X minutes as configured by
     * geofence.expiration.cleanup.interval-minutes
     * (default: 5 minutes).
     */
    @Scheduled(fixedDelayString = "#{${geofence.expiration.cleanup.interval-minutes:5} * 60 * 1000}")
    public void cleanupExpiredGeofenceDeployments() {
        try {
            log.debug("Starting scheduled cleanup of expired Geofence deployments (grace period: {} hours)",
                    gracePeriodHours);

            int deactivatedCount = geofenceDeploymentService.deactivateExpiredGeofenceDeployments();

            if (deactivatedCount > 0) {
                log.info(
                        "Scheduled cleanup completed: {} expired Geofence deployments deactivated (grace period: {} hours)",
                        deactivatedCount, gracePeriodHours);
            } else {
                log.debug("Scheduled cleanup completed: no expired Geofence deployments found (grace period: {} hours)",
                        gracePeriodHours);
            }

        } catch (Exception e) {
            log.error("Error during scheduled cleanup of expired Geofence deployments", e);
        }
    }

    /**
     * Get the current cleanup interval in minutes
     * 
     * @return The cleanup interval in minutes
     */
    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    /**
     * Get the current grace period in hours
     * 
     * @return The grace period in hours
     */
    public int getGracePeriodHours() {
        return gracePeriodHours;
    }
}
