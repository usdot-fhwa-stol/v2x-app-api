package usdot.v2x.app.api.models.etx.configuration.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.geofence.FrameType;
import lombok.Data;

public @Data class DataFrame {
    @JsonProperty("notUsed")
    private Integer notUsed;
    @JsonProperty("frameType")
    private FrameType frameType;

}
