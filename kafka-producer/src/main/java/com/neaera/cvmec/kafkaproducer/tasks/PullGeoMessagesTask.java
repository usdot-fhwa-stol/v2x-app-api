package com.neaera.cvmec.kafkaproducer.tasks;

import com.neaera.cvmec.kafkaproducer.services.PostgresService;
import com.neaera.cvmec.kafkaproducer.services.GeohashCacheService;
import com.neaera.cvmec.kafkaproducer.services.PostgresNotificationService.TableUpdateNotificationEvent;
import com.neaera.cvmec.kafkaproducer.models.postgres.derived.GeohashPayloadMessage;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PullGeoMessagesTask {

    private final PostgresService postgresService;
    private final GeohashCacheService geohashCacheService;

    @Autowired
    public PullGeoMessagesTask(PostgresService postgresService, GeohashCacheService geohashCacheService) {
        this.postgresService = postgresService;
        this.geohashCacheService = geohashCacheService;
    }

    /**
     * Handles table update notifications from PostgreSQL LISTEN/NOTIFY
     * This method is triggered whenever a notification is received on the
     * 'table_updates' channel
     */
    @EventListener
    @Async
    public void handleTableUpdateNotification(TableUpdateNotificationEvent event) {
        log.info("Received table update notification, refreshing geohash payload messages. Payload: {}",
                event.getPayload());

        try {
            // Fetch messages from database
            List<GeohashPayloadMessage> messages = postgresService.getGeohashPayloadMessages();

            // Update the thread-safe cache
            geohashCacheService.updateCache(messages);

            // Log summary information
            log.info("Retrieved and cached {} geohash payload messages. Cache size: {}",
                    messages.size(), geohashCacheService.getCacheSize());
        } catch (Exception e) {
            log.error("Error occurred while pulling geohash payload messages after notification", e);
        }
    }

    /**
     * Manual method to refresh cache - can be called for initial load or manual
     * refresh
     * This method can be called during application startup to populate the initial
     * cache
     */
    public void refreshGeohashPayloadMessages() {
        log.info("Manually refreshing geohash payload messages");

        try {
            // Fetch messages from database
            List<GeohashPayloadMessage> messages = postgresService.getGeohashPayloadMessages();

            // Update the thread-safe cache
            geohashCacheService.updateCache(messages);

            // Log summary information
            log.info("Retrieved and cached {} geohash payload messages. Cache size: {}",
                    messages.size(), geohashCacheService.getCacheSize());
        } catch (Exception e) {
            log.error("Error occurred while manually refreshing geohash payload messages", e);
        }
    }

}