package usdot.v2x.app.api.config.etx;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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
    private Double sessionTokenLifespanMinutes;
    private Boolean tokenPeriodicRegenerationEnabled;
}
