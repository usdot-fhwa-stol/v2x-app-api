package usdot.v2x.app.api.models.etx.configuration.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class GeographicalPath {
    @JsonProperty("description")
    private Object description;
    @JsonProperty("direction")
    private String direction;
}
