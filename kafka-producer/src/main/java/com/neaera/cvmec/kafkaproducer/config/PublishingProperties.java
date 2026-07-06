package com.neaera.cvmec.kafkaproducer.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kafka-producer.publishing")
@Data
public class PublishingProperties {

    private static final Duration DEFAULT_BATCH_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Fixed thread pool size for parallel Kafka publishing. When unset or zero, defaults to
     * {@code min(availableProcessors * 2, 20)}.
     */
    private int threadPoolSize;

    /**
     * Publishing frequency in Hz. When unset or zero, defaults to 1 Hz (1000 ms fixed rate).
     */
    private double frequencyHz;

    /**
     * Maximum time to wait for a publish cycle to finish. Supports Spring duration format
     * (e.g. {@code 5s}, {@code 500ms}, {@code 1m}).
     */
    private Duration batchTimeout = DEFAULT_BATCH_TIMEOUT;

    public long getFixedRateMs() {
        return resolveFixedRateMs(frequencyHz);
    }

    public long getBatchTimeoutMillis() {
        return resolveBatchTimeoutMillis(batchTimeout);
    }

    public static long resolveFixedRateMs(double configuredFrequencyHz) {
        if (configuredFrequencyHz > 0) {
            return Math.round(1000.0 / configuredFrequencyHz);
        }
        return 1000L;
    }

    public static long resolveBatchTimeoutMillis(Duration configuredBatchTimeout) {
        if (configuredBatchTimeout != null && !configuredBatchTimeout.isZero() && !configuredBatchTimeout.isNegative()) {
            return configuredBatchTimeout.toMillis();
        }
        return DEFAULT_BATCH_TIMEOUT.toMillis();
    }

    public double getEffectiveFrequencyHz() {
        return frequencyHz > 0 ? frequencyHz : 1.0;
    }
}
