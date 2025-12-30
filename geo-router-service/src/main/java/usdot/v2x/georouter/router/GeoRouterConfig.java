package usdot.v2x.georouter.router;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Geo Router Service.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "geo-routing.routing")
public class GeoRouterConfig {
    private boolean enabled = true;
    private int maxSubscribersPerMessage = 1000;
    private int messageTtlSeconds = 10;
}


