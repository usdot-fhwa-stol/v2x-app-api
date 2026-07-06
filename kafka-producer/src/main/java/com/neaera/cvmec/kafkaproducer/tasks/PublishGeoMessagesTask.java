package com.neaera.cvmec.kafkaproducer.tasks;

import com.neaera.cvmec.kafkaproducer.config.PublishingProperties;
import com.neaera.cvmec.kafkaproducer.models.GeoHashRoutedMsg;
import com.neaera.cvmec.kafkaproducer.services.GeohashCacheService;
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
    private final long batchTimeoutMillis;

    @Autowired
    public PublishGeoMessagesTask(
            GeohashCacheService geohashCacheService,
            KafkaProducerService kafkaProducerService,
            PublishingProperties publishingProperties) {
        this.geohashCacheService = geohashCacheService;
        this.kafkaProducerService = kafkaProducerService;
        this.batchTimeoutMillis = publishingProperties.getBatchTimeoutMillis();
        int threadPoolSize = resolveThreadPoolSize(publishingProperties.getThreadPoolSize());
        this.messagePublishingExecutor = Executors.newFixedThreadPool(threadPoolSize);
        log.info(
                "Message publishing configured: {} Hz ({} ms interval), thread pool size {}, batch timeout {} ms",
                publishingProperties.getEffectiveFrequencyHz(),
                publishingProperties.getFixedRateMs(),
                threadPoolSize,
                batchTimeoutMillis);
    }

    static int resolveThreadPoolSize(int configuredThreadPoolSize) {
        if (configuredThreadPoolSize > 0) {
            return configuredThreadPoolSize;
        }
        return Math.min(Runtime.getRuntime().availableProcessors() * 2, 20);
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
     * Publishes cached messages to Kafka at a configurable fixed rate (default 1 Hz).
     * This runs independently of cache updates to maintain consistent publishing
     * frequency but is still thread-safe due to the underlying cache implementation
     */
    @Scheduled(fixedRateString = "#{@publishingProperties.fixedRateMs}")
    public void publishCachedMessages() {
        try {
            List<GeoHashRoutedMsg> cachedMessages = geohashCacheService.getAllMessages();

            if (cachedMessages.isEmpty()) {
                log.debug("No cached messages to publish");
                return;
            }

            log.debug(
                    "Publishing {} cached geohash messages to Kafka: {}",
                    cachedMessages.size(),
                    cachedMessages.stream().map(GeoHashRoutedMsg::toLogDescription).collect(Collectors.joining("; ")));

            List<CompletableFuture<Void>> publishingTasks = cachedMessages.stream()
                    .map(message -> CompletableFuture.runAsync(() -> {
                        try {
                            kafkaProducerService.sendGeoHashRoutedMsgAsProtobuf(message);
                            log.debug("Published message: {}", message.toLogDescription());
                        } catch (Exception e) {
                            log.error("Failed to publish message: {}", message.toLogDescription(), e);
                        }
                    }, messagePublishingExecutor))
                    .collect(Collectors.toList());

            CompletableFuture<Void> allPublishingTasks = CompletableFuture.allOf(
                    publishingTasks.toArray(new CompletableFuture[0]));

            allPublishingTasks.get(batchTimeoutMillis, TimeUnit.MILLISECONDS);

            log.debug(
                    "Successfully published {} cached geohash messages in parallel: {}",
                    cachedMessages.size(),
                    cachedMessages.stream().map(GeoHashRoutedMsg::toLogDescription).collect(Collectors.joining("; ")));
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn(
                    "Publishing timed out after {} ms. Some messages may still be processing.",
                    batchTimeoutMillis);
        } catch (Exception e) {
            log.error("Error occurred while publishing cached geohash messages", e);
        }
    }
}
