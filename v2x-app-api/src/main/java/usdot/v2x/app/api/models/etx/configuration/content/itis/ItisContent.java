package usdot.v2x.app.api.models.etx.configuration.content.itis;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class ItisContent {
    @JsonProperty("item")
    private ItisItem item;
}
