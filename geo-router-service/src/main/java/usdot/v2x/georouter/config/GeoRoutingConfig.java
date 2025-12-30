package usdot.v2x.georouter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for geospatial routing.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "geo-routing")
public class GeoRoutingConfig {

    private Geohash geohash = new Geohash();
    private Routing routing = new Routing();

    @Data
    public static class Geohash {
        private int precision = 7;
        private int neighborRadius = 1;
    }

    @Data
    public static class Routing {
        private boolean enabled = true;
        private int maxSubscribersPerMessage = 1000;
        private int messageTtlSeconds = 10;
    }
}


