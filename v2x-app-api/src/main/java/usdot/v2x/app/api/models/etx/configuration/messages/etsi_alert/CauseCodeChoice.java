package usdot.v2x.app.api.models.etx.configuration.messages.etsi_alert;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * The main cause of a detected event. Each entry is of a different type and
 * represents the sub cause code.
 * Only one of the cause codes can be set at a time, all others will be null
 */
public @Data class CauseCodeChoice {
    @JsonProperty("trafficCondition1")
    private Integer trafficCondition1;
    @JsonProperty("accident2")
    private Integer accident2;
    @JsonProperty("roadworks3")
    private Integer roadworks3;
    @JsonProperty("impassability5")
    private Integer impassability5;
    @JsonProperty("wrongWaDriving14")
    private Integer wrongWaDriving14;
    @JsonProperty("emergencyVehicleApproaching95")
    private Integer emergencyVehicleApproaching95;
}