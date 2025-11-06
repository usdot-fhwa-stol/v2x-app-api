package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class Management {
    @JsonProperty("actionId")
    private ActionId actionId;
    @JsonProperty("detectionTime")
    private int detectionTime;
    @JsonProperty("referenceTime")
    private int referenceTime;
    @JsonProperty("eventPosition")
    private EventPosition eventPosition;
    @JsonProperty("awarenessDistance")
    private AwarenessDistance awarenessDistance;
    @JsonProperty("stationType")
    private int stationType;
}
