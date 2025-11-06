package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public @Data class RegistrationResponse {
    @JsonProperty("DeviceID")
    private String deviceId;
    @JsonProperty("Certificate")
    private usdot.v2x.app.api.models.etx.registration.Certificate certificate;
}
