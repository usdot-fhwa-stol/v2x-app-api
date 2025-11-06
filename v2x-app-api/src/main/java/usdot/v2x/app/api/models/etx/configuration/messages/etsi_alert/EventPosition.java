package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class EventPosition {
    @JsonProperty("latitude")
    private int latitude;
    @JsonProperty("longitude")
    private int longitude;
}
