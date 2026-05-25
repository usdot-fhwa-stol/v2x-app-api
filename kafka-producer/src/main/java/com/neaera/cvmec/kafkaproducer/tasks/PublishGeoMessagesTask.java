package com.neaera.cvmec.kafkaproducer.tasks;

import com.neaera.cvmec.kafkaproducer.services.GeohashCacheService;
import com.neaera.cvmec.kafkaproducer.models.GeoHashRoutedMsg;
import com.neaera.cvmec.kafkaproducer.services.KafkaProducerService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PublishGeoMessagesTask {
    private final GeohashCacheService geohashCacheService;
    private final KafkaProducerService kafkaProducerService;
    private final ExecutorService messagePublishingExecutor;

    @Autowired
    public PublishGeoMessagesTask(GeohashCacheService geohashCacheService, KafkaProducerService kafkaProducerService) {
        this.geohashCacheService = geohashCacheService;
        this.kafkaProducerService = kafkaProducerService;
        // Create a thread pool with optimal size for message publishing
        this.messagePublishingExecutor = Executors.newFixedThreadPool(
                Math.min(Runtime.getRuntime().availableProcessors() * 2, 20));
    }

    /**
     * Shutdown the executor service gracefully when the application stops
     */
    @PreDestroy
    public void shutdown() {
        if (messagePublishingExecutor != null && !messagePublishingExecutor.isShutdown()) {
            log.debug("Shutting down message publishing executor");
            messagePublishingExecutor.shutdown();
            try {
                if (!messagePublishingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Executor did not terminate gracefully, forcing shutdown");
                    messagePublishingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for executor termination", e);
                messagePublishingExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Publishes cached messages to Kafka at 1Hz frequency
     * This runs independently of cache updates to maintain consistent publishing
     * frequency but is still thread-safe due to the underlying cache implementation
     */
    @Scheduled(fixedRateString = "1000") // Runs every second (1Hz)
    public void publishCachedMessages() {
        log.debug("Publishing cached geohash messages to Kafka");

        try {
            // Fetch messages from cache
            List<GeoHashRoutedMsg> cachedMessages = geohashCacheService.getAllMessages();

            if (cachedMessages.isEmpty()) {
                log.debug("No cached messages to publish");
                return;
            }

            // Publish messages in parallel using CompletableFuture
            List<CompletableFuture<Void>> publishingTasks = cachedMessages.stream()
                    .map(message -> CompletableFuture.runAsync(() -> {
                        try {
                            // Use protobuf serialization
                            kafkaProducerService.sendGeoHashRoutedMsgAsProtobuf(message);
                        } catch (Exception e) {
                            log.error("Failed to publish message: {}", message, e);
                        }
                    }, messagePublishingExecutor))
                    .collect(Collectors.toList());

            // Wait for all tasks to complete
            CompletableFuture<Void> allPublishingTasks = CompletableFuture.allOf(
                    publishingTasks.toArray(new CompletableFuture[0]));

            // Set a reasonable timeout to avoid blocking indefinitely
            allPublishingTasks.get(5, TimeUnit.SECONDS);

            log.debug("Successfully published {} cached geohash messages in parallel", cachedMessages.size());
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Publishing timed out after 5 seconds. Some messages may still be processing.");
        } catch (Exception e) {
            log.error("Error occurred while publishing cached geohash messages", e);
        }
    }
}
