package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * The type of event including direct and sub cause
 */
public @Data class EventType {
    @JsonProperty("ccAndScc")
    private CauseCodeChoice ccAndScc;
}