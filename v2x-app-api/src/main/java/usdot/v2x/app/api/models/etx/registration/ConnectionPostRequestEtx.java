package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.Geolocation;
import usdot.v2x.app.api.models.etx.NetworkType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

public @Data class ConnectionPostRequestEtx {
    @JsonProperty("DeviceID")
    @Schema(description = "Unique identifier for the device", required = true)
    private String deviceId;
    @JsonProperty("Geolocation")
    @Schema(description = "Geographic location information for the device", required = true)
    private Geolocation geolocation;
    @JsonProperty("NetworkType")
    @Schema(description = "Type of network connection", required = true)
    private NetworkType networkType;

    public ConnectionPostRequestEtx(ConnectionPostRequest request) {
        this.deviceId = request.getDeviceId();
        this.geolocation = new Geolocation(request);
        this.networkType = request.getNetworkType();
    }
}
