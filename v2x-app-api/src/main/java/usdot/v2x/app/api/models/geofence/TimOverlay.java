package usdot.v2x.app.api.models.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimOverlay {
    @JsonProperty("majorFontSize")
    private Integer majorFontSize;

    @JsonProperty("minorFontSize")
    private Integer minorFontSize;

    @JsonProperty("xPos")
    private Integer xPos;

    @JsonProperty("yPos")
    private Integer yPos;

    @JsonProperty("align")
    private String align;
}
