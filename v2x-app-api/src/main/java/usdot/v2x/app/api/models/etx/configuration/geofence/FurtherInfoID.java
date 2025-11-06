package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class FurtherInfoID {
    @JsonProperty("furtherInfoID")
    private String furtherInfoID;
}
