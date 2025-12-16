package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class Position {
    @JsonProperty("lat")
    private Integer lat;
    @JsonProperty("lng")
    private Integer lng;
}
