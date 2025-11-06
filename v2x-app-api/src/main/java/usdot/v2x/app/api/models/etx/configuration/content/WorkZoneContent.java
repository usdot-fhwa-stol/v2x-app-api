package usdot.v2x.app.api.models.etx.configuration.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.content.itis.ItisContent;
import lombok.Data;

import java.util.List;

public @Data non-sealed class WorkZoneContent implements SaeInfoContent {
    @JsonProperty("workZone")
    private List<ItisContent> workZone;
}
