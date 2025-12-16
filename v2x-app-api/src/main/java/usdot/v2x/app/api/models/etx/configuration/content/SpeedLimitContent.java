package usdot.v2x.app.api.models.etx.configuration.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.content.itis.ItisContent;
import lombok.Data;

import java.util.List;

public @Data non-sealed class SpeedLimitContent implements SaeInfoContent {
    @JsonProperty("speedLimit")
    private List<ItisContent> speedLimit;
}
