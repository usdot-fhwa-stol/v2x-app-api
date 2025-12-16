package usdot.v2x.app.api.models.etx.configuration.content.itis;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data non-sealed class ItisItemItis implements ItisItem {
    @JsonProperty("itis")
    private Integer itis;
}
