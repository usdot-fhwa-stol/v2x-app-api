package usdot.v2x.app.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geofence")
public class GeofenceProperties {

    private Geohash geohash = new Geohash();
    private Limits limits = new Limits();

    @Data
    public static class Geohash {
        private int precision = 7;
    }

    @Data
    public static class Limits {
        private int maxGeohashes = 500;
    }
}
