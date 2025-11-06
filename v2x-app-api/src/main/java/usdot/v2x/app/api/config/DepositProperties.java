package usdot.v2x.app.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "deposit")
public class DepositProperties {

    private Mode mode = Mode.ETX_CONFIGURATION_API;

    public enum Mode {
        ETX_CONFIGURATION_API,
        GEOFENCE_MQTT
    }
}
