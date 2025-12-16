package usdot.v2x.app.api.models.etx.configuration.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.content.itis.ItisContent;
import lombok.Data;

import java.util.List;

public @Data non-sealed class ExitServiceContent implements SaeInfoContent {
    @JsonProperty("exitService")
    private List<ItisContent> exitService;
}
