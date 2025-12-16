package usdot.v2x.app.api.models.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimPhrase {
    @JsonProperty("name")
    private String name;

    @JsonProperty("source")
    private String source;

    @JsonProperty("type")
    private String type;

    @JsonProperty("codes")
    private List<String> codes;

    @JsonProperty("graphic")
    private String graphic;

    @JsonProperty("overlays")
    private List<TimOverlay> overlays;
}
