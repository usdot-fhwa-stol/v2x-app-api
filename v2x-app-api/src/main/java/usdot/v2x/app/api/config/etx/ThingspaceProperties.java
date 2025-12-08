package usdot.v2x.app.api.config.etx;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration properties for Thingspace API integration.
 */
@Configuration
@ConfigurationProperties(prefix = "thingspace")
@Data
public class ThingspaceProperties {
    private Boolean enabled;
    private String endpoint;
    private String key;
    private String secret;
    /**
     * Session token lifespan duration. Supports Spring Boot duration format (e.g.,
     * "10m", "30s", "1h")
     * or ISO-8601 duration format (e.g., "PT10M", "PT30S", "PT1H").
     */
    private Duration sessionTokenLifespan;
    private Boolean tokenPeriodicRegenerationEnabled;
}
