package usdot.v2x.app.api.models.etx.configuration.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public @Data class GenericMessage extends Message {
    @JsonProperty("generic")
    private GenericMessageItem generic;
}
