package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class Altitude {
    @JsonProperty("altitudeValue")
    private int altitudeValue;
    @JsonProperty("altitudeConfidence")
    private AltitudeConfidence altitudeConfidence;
    @JsonProperty("semiMajorOrientation")
    private int semiMajorOrientation;
}
