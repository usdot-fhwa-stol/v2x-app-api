package usdot.v2x.app.api.models.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@Schema(description = "Response model for Geofence deployment")
public class GeofenceDeploymentResponse {

    @JsonProperty("id")
    @Schema(description = "Database ID of the Geofence deployment")
    private Long id;

    @JsonProperty("geofence_id")
    @Schema(description = "Unique identifier for the Geofence deployment")
    private String geofenceId;

    @JsonProperty("geojson")
    @Schema(description = "GeoJSON FeatureCollection representation of the deployment area", implementation = GeofenceFeatureCollection.class)
    private GeofenceFeatureCollection geojson;

    @JsonProperty("hex_payload")
    @Schema(description = "ASN.1 encoded message in hexadecimal format")
    private String hexPayload;

    @JsonProperty("deployed_by")
    @Schema(description = "Username of the person who deployed the Geofence")
    private String deployedBy;

    @JsonProperty("is_active")
    @Schema(description = "Whether the Geofence deployment is currently active")
    private Boolean isActive;

    @JsonProperty("created_at")
    @Schema(description = "Timestamp when the Geofence was deployed")
    private Instant createdAt;

    @JsonProperty("updated_at")
    @Schema(description = "Timestamp when the Geofence was last updated")
    private Instant updatedAt;

    @JsonProperty("expires_at")
    @Schema(description = "Optional expiration timestamp for the Geofence deployment")
    private Instant expiresAt;

    @JsonProperty("geohashes")
    @Schema(description = "List of geohashes covering the deployment area")
    private List<String> geohashes;
}
