package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class Header {
    @JsonProperty("protocolVersion")
    private int protocolVersion;
    @JsonProperty("messageId")
    private int messageId;
    @JsonProperty("stationId")
    private int stationId;
}
