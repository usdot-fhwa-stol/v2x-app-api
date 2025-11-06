package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.NetworkType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

public @Data class ConnectionPostRequest {
    @JsonProperty("DeviceID")
    @Schema(description = "Unique identifier for the device", required = true)
    private String deviceId;
    @JsonProperty("lat")
    @Schema(description = "Latitude coordinate of the device location", required = true)
    private Double lat;
    @JsonProperty("long")
    @Schema(description = "Longitude coordinate of the device location", required = true)
    private Double lon;
    @JsonProperty("NetworkType")
    @Schema(description = "Type of network connection", required = true)
    private NetworkType networkType;
}
