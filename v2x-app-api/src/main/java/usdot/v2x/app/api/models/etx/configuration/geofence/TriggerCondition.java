package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TriggerCondition {
    @JsonProperty("enter")
    enter,
    @JsonProperty("leave")
    leave,
    @JsonProperty("inside")
    inside,
    @JsonProperty("crossing")
    crossing
}
