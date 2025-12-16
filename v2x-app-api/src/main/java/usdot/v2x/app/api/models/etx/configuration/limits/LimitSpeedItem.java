package usdot.v2x.app.api.models.etx.configuration.limits;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data non-sealed class LimitSpeedItem implements Limit {
    @JsonProperty("description")
    private String description;
    @JsonProperty("speed")
    private LimitItem speed;
}
