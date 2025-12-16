package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
public @Data class CompleteResponse {
    @JsonProperty("registration")
    private RegistrationResponse registration;
    @JsonProperty("connection")
    private ConnectionResponse connection;
}
