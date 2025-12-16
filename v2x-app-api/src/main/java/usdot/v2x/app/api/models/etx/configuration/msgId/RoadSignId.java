package usdot.v2x.app.api.models.etx.configuration.msgId;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.geofence.Position;
import lombok.Data;

public @Data non-sealed class RoadSignId implements MessageId {
    @JsonProperty("position")
    private Position position;
    @JsonProperty("viewAngle")
    private String viewAngle;
}
