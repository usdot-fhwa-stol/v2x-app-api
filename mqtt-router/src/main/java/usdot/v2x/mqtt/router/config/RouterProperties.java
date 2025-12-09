package usdot.v2x.mqtt.router.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "router")
public class RouterProperties {
    private boolean enabled = true;
    private Duration pollInterval = Duration.ofSeconds(5);

    // Explicit getter for boolean (Lombok @Data should generate this, but adding
    // for clarity)
    public boolean isEnabled() {
        return enabled;
    }
}
