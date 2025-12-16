package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AwarenessDistance {
    @JsonProperty("lessThan50m")
    lessThan50m,
    @JsonProperty("lessThan100m")
    lessThan100m,
    @JsonProperty("lessThan200m")
    lessThan200m,
    @JsonProperty("lessThan500m")
    lessThan500m,
    @JsonProperty("lessThan1000m")
    lessThan1000m,
    @JsonProperty("lessThan5km")
    lessThan5km,
    @JsonProperty("lessThan10km")
    lessThan10km,
    @JsonProperty("over10km")
    over10km
}