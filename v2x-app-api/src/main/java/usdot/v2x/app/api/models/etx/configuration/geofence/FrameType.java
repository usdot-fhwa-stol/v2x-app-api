package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FrameType {
    @JsonProperty("unknown")
    unknown,
    @JsonProperty("advisory")
    advisory,
    @JsonProperty("roadSignage")
    roadSignage,
    @JsonProperty("commercialSignage")
    commercialSignage
}
