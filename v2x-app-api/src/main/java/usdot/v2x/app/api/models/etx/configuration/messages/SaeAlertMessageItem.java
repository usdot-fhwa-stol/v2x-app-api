package usdot.v2x.app.api.models.etx.configuration.messages;

import lombok.Data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public @Data class SaeAlertMessageItem {
    @JsonProperty("typeEvent")
    private Integer typeEvent;
    @JsonProperty("description")
    private List<Integer> description;
}
