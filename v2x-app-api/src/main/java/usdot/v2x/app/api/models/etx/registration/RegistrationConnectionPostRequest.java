package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.NetworkType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

public @Data class RegistrationConnectionPostRequest {
    @JsonProperty("ClientType")
    @Schema(description = "Type of client for registration", required = true)
    private RegistrationClientType clientType;
    @JsonProperty("ClientSubtype")
    @Schema(description = "Subtype of client for registration", required = true)
    private RegistrationClientSubType clientSubtype;
    @JsonProperty("lat")
    @Schema(description = "Latitude coordinate of the device location", required = true)
    private Double lat;
    @JsonProperty("long")
    @Schema(description = "Longitude coordinate of the device location", required = true)
    private Double lon;
    @JsonProperty("NetworkType")
    @Schema(description = "Type of network connection", required = true)
    private NetworkType networkType;

    @JsonIgnore
    public RegistrationPostRequest getRegistrationPostRequest() {
        RegistrationPostRequest registrationPostRequest = new RegistrationPostRequest();
        registrationPostRequest.setClientType(clientType);
        registrationPostRequest.setClientSubtype(clientSubtype);
        return registrationPostRequest;
    }

    @JsonIgnore
    public ConnectionPostRequest getConnectionPostRequest(String deviceId) {
        ConnectionPostRequest connectionPostRequest = new ConnectionPostRequest();
        connectionPostRequest.setDeviceId(deviceId);
        connectionPostRequest.setLat(lat);
        connectionPostRequest.setLon(lon);
        connectionPostRequest.setNetworkType(networkType);
        return connectionPostRequest;
    }
}
