package usdot.v2x.app.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for geofence operations.
 */
@Data
@Component
@ConfigurationProperties(prefix = "geofence")
public class GeofenceProperties {

    private Geohash geohash = new Geohash();
    private Limits limits = new Limits();
    private Expiration expiration = new Expiration();

    @Data
    public static class Geohash {
        private int precision = 7;
        /**
         * Maximum number of CPU workers shared by polygon component scans. A value
         * of one disables parallel analysis. Runtime parallelism is also capped at
         * the number of processors visible to the JVM.
         */
        private int polygonAnalysisParallelism = 4;
    }

    @Data
    public static class Limits {
        private int maxGeohashes = 500;
        /**
         * Maximum number of precision-level cells that may be inspected while
         * building one deployment. This bounds polygon-envelope scans before any
         * per-cell geometry work is performed.
         */
        private long maxCandidateCells = 100_000;
        private int minGeohashes = 4;
        private boolean allowOverlappingGeohashes = false;
        /**
         * Intersecting-cell count at which 3×3 representative thinning begins.
         * Deployments with this many cells or fewer keep every available
         * intersecting geohash. Larger deployments are thinned to one
         * representative per 3×3 block. Values of {@code 0} or less always thin.
         */
        private int representativeFilterThreshold = 20;
        /**
         * Maximum number of 3×3-representative geohashes an override geofence may
         * cover when it is kept. If the override exceeds this count, the API
         * generates a tighter geofence from the TIM/MAP message instead. Values of
         * {@code 0} or less always generate from the message. MQTT deposits that
         * keep the override persist every intersecting cell, not these representatives.
         */
        private int overrideGeohashThreshold = 50;
    }

    @Data
    public static class Expiration {
        private Cleanup cleanup = new Cleanup();
        private Duration gracePeriod;

        @Data
        public static class Cleanup {
            private boolean enabled = true;
            /**
             * Cleanup interval duration. Supports Spring Boot duration format (e.g.,
             * "5m", "30s", "1h")
             * or ISO-8601 duration format (e.g., "PT5M", "PT30S", "PT1H").
             */
            private Duration interval;
        }
    }
}
