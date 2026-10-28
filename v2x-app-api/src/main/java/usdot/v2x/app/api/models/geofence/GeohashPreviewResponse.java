package usdot.v2x.app.api.models.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Preview of geohashes that would be selected for a V2X message deposit")
public class GeohashPreviewResponse {

    @JsonProperty("geofence_id")
    @Schema(description = "Identifier that would be assigned to this deployment")
    private String geofenceId;

    @JsonProperty("geofence")
    @Schema(description = "Geofence used for geohash selection (override or extracted from message)")
    private GeofenceFeatureCollection geofence;

    @JsonProperty("geohashes")
    @Schema(description = "Geohashes that would be published to for this deployment")
    private List<String> geohashes;

    @JsonProperty("geohash_count")
    @Schema(description = "Number of geohashes selected")
    private int geohashCount;
}
