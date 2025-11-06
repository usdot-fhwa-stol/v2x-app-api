package usdot.v2x.app.api.models.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimConfigurationResponse {
    @JsonProperty("version")
    private String version;

    @JsonProperty("tims")
    private List<TimPhrase> tims;
}
