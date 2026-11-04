package usdot.v2x.app.api.models.etx.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.geofence.GeofenceFeatureCollection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request model for V2X message deposit")
public class DepositRequest {
    @JsonProperty("asn1_hex")
    @Schema(description = "UPER-encoded MessageFrame containing a MAP or TIM V2X Message in hexadecimal format", required = true)
    private String asn1Hex;

    @JsonProperty("override_geofence")
    @Schema(description = "Override Geofence for ETX deployment. If not set, the geofence will be extracted from the provided message. "
            + "If the override covers more geohashes than geofence.limits.override-geohash-threshold, "
            + "the partner API generates a geofence from the message instead. "
            + "MQTT deposits that keep the override persist every intersecting geohash cell rather than 3×3 representatives.", required = false)
    private GeofenceFeatureCollection overrideGeofence;
}