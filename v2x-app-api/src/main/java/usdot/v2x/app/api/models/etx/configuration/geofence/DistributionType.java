package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DistributionType {
    @JsonProperty("Targeted")
    Targeted,
    @JsonProperty("Broadcast")
    Broadcast
}