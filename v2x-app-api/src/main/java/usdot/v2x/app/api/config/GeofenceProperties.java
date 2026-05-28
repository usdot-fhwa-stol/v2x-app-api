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
    }

    @Data
    public static class Limits {
        private int maxGeohashes = 500;
        private int minGeohashes = 4;
        private boolean allowOverlappingGeohashes = false;
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
