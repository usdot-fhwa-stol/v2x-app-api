package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class Situation {
    @JsonProperty("informationQuality")
    private int informationQuality;
    @JsonProperty("eventType")
    private EventType eventType;
}
