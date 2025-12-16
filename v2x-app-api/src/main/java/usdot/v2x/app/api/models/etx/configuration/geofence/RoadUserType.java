package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RoadUserType {
    @JsonProperty("VulnerableRoadUser")
    VulnerableRoadUser,
    @JsonProperty("Vehicle")
    Vehicle
}
