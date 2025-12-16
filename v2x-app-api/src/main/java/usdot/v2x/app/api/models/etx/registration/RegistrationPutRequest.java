package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

public @Data class RegistrationPutRequest {
    @JsonProperty("DeviceID")
    @Schema(description = "Unique identifier for the device to update registration", required = true)
    private String deviceId;
}
