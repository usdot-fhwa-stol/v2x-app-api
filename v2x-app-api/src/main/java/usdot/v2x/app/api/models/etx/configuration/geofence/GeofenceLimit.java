package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class GeofenceLimit {
    @JsonProperty("speed")
    private GeofenceLimitSpeed speed;
}
