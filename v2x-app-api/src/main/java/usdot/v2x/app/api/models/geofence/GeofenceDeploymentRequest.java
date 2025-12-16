package usdot.v2x.app.api.models.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@Schema(description = "Request model for Geofence deployment")
public class GeofenceDeploymentRequest {

    @JsonProperty("geofence_id")
    @NotBlank(message = "Geofence ID cannot be blank")
    @Schema(description = "Unique identifier for the Geofence deployment", required = true, example = "GEOFENCE_12345")
    private String geofenceId;

    @JsonProperty("geojson")
    @NotNull(message = "GeoJSON cannot be null")
    @Schema(description = "GeoJSON FeatureCollection representation of the deployment area", implementation = GeofenceFeatureCollection.class, required = true)
    private GeofenceFeatureCollection geojson;

    @JsonProperty("hex_payload")
    @NotBlank(message = "Hex payload cannot be blank")
    @Schema(description = "ASN.1 encoded message in hexadecimal format", required = true)
    private String hexPayload;

    @JsonProperty(value = "msg_type", access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "V2X message type (e.g., TIM, MAP). Populated by server.", required = false, example = "TIM")
    private String msgType;

    @JsonProperty("deployed_by")
    @NotBlank(message = "Deployed by cannot be blank")
    @Schema(description = "Username of the person deploying the Geofence", required = true, example = "admin")
    private String deployedBy;

    @JsonProperty("geohashes")
    @NotNull(message = "Geohashes cannot be null")
    @Schema(description = "List of geohashes covering the deployment area", required = true)
    private List<String> geohashes;

    @JsonProperty("expires_at")
    @Schema(description = "Optional expiration timestamp for the Geofence deployment")
    private String expiresAt;
}
