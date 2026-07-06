package com.neaera.cvmec.kafkaproducer.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.neaera.cvmec.kafkaproducer.config.PublishingProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PublishGeoMessagesTaskTest {

    @Test
    void resolveThreadPoolSize_usesConfiguredValueWhenPositive() {
        assertEquals(32, PublishGeoMessagesTask.resolveThreadPoolSize(32));
    }

    @Test
    void resolveThreadPoolSize_usesDefaultWhenZeroOrNegative() {
        int expectedDefault = Math.min(Runtime.getRuntime().availableProcessors() * 2, 20);

        assertEquals(expectedDefault, PublishGeoMessagesTask.resolveThreadPoolSize(0));
        assertEquals(expectedDefault, PublishGeoMessagesTask.resolveThreadPoolSize(-1));
    }

    @Test
    void resolveFixedRateMs_usesConfiguredFrequencyWhenPositive() {
        assertEquals(500L, PublishingProperties.resolveFixedRateMs(2));
        assertEquals(2000L, PublishingProperties.resolveFixedRateMs(0.5));
    }

    @Test
    void resolveFixedRateMs_usesDefaultWhenZeroOrNegative() {
        assertEquals(1000L, PublishingProperties.resolveFixedRateMs(0));
        assertEquals(1000L, PublishingProperties.resolveFixedRateMs(-1));
    }

    @Test
    void resolveBatchTimeoutMillis_usesConfiguredDurationWhenPositive() {
        assertEquals(500L, PublishingProperties.resolveBatchTimeoutMillis(Duration.ofMillis(500)));
        assertEquals(60_000L, PublishingProperties.resolveBatchTimeoutMillis(Duration.ofMinutes(1)));
    }

    @Test
    void resolveBatchTimeoutMillis_usesDefaultWhenMissingOrNonPositive() {
        assertEquals(5000L, PublishingProperties.resolveBatchTimeoutMillis(null));
        assertEquals(5000L, PublishingProperties.resolveBatchTimeoutMillis(Duration.ZERO));
        assertEquals(5000L, PublishingProperties.resolveBatchTimeoutMillis(Duration.ofMillis(-1)));
    }
}
