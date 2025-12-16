package usdot.v2x.app.api.models.etx.configuration.geofence;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Schema(description = "GeoJSON FeatureCollection representing a geofence area")
public class GeofenceFeatureCollection {
    @JsonProperty("type")
    @Schema(description = "GeoJSON type, must be 'FeatureCollection'", example = "FeatureCollection")
    private String type;

    @JsonProperty("features")
    @Schema(description = "Array of GeoJSON features defining the geofence geometry")
    private List<GeofenceFeature> features;
}
