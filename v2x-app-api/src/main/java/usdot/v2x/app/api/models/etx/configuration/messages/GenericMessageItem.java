package usdot.v2x.app.api.models.etx.configuration.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class GenericMessageItem {
    @JsonProperty("messageType")
    private String messageType;
    @JsonProperty("messageFormat")
    private String messageFormat;
    @JsonProperty("payload")
    private String payload;
}
