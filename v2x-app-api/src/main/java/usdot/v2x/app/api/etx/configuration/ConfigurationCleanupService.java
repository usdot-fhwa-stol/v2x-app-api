package usdot.v2x.app.api.etx.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationClearGeofence;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for periodically cleaning up inactive TIM geofences.
 * This service runs every 5 minutes and clears geofences containing inactive
 * TIM messages.
 */
@Slf4j
@Service
public class ConfigurationCleanupService {

    private final ConfigurationApi configurationApi;
    private final Boolean enabled;

    public ConfigurationCleanupService(@Autowired(required = false) ConfigurationApi configurationApi,
            EtxProperties etxProperties) {
        this.enabled = etxProperties.getEnabled();
        this.configurationApi = configurationApi;
    }

    @Scheduled(fixedRateString = "${etx.configuration.cleanup.interval-minutes}", timeUnit = TimeUnit.MINUTES)
    @ConditionalOnProperty(value = { "etx.configuration.cleanup.enabled" }, havingValue = "true")
    public void clearInactiveTimGeofences() {
        if (!enabled) {
            log.debug("Configuration cleanup is disabled, skipping scheduled cleanup of inactive TIM geofences");
            return;
        }
        log.debug("Starting scheduled cleanup of inactive TIM geofences");

        try {
            ConfigurationClearGeofence request = new ConfigurationClearGeofence();
            request.setClearTimOnly(true);

            List<String> clearedIds = configurationApi.clearGeofences(request).getBody();

            if (clearedIds != null && !clearedIds.isEmpty()) {
                log.info("Successfully cleared {} inactive TIM geofences: {}", clearedIds.size(), clearedIds);
            } else {
                log.debug("No inactive TIM geofences found to clear");
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to process geofence data during cleanup: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during geofence cleanup: {}", e.getMessage(), e);
            // Don't rethrow - let the scheduled task continue running
            log.warn("Geofence cleanup will retry on next scheduled run");
        }
    }
}