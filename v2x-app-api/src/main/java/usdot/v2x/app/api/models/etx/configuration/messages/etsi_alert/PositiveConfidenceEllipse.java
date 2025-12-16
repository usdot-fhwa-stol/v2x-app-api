package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class PositiveConfidenceEllipse {
    @JsonProperty("semiMajorConfidence")
    private int semiMajorConfidence;
    @JsonProperty("semiMinorConfidence")
    private int semiMinorConfidence;
    @JsonProperty("semiMajorOrientation")
    private int semiMajorOrientation;
}
