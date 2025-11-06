package usdot.v2x.app.api.models.etx.configuration.geometry;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@EqualsAndHashCode(callSuper = true)
public class LineString extends Geometry {
    @JsonProperty("coordinates")
    private List<List<Double>> coordinates;
}
