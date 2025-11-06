package usdot.v2x.app.api.models.etx.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ConfigurationClearGeofence {
    @JsonProperty("clear_tim_only")
    private boolean clearTimOnly;
}