package usdot.v2x.app.api.models.etx.configuration.limits;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class LimitItem {
    @JsonProperty("min")
    private Double min;
    @JsonProperty("max")
    private Double max;
}
