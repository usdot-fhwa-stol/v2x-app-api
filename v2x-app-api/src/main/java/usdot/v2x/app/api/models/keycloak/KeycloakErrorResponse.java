package usdot.v2x.app.api.models.keycloak;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public @Data class KeycloakErrorResponse {
    @JsonProperty("timestamp")
    private BigInteger timestamp;
    @JsonProperty("status")
    private int status;
    @JsonProperty("error")
    private String error;
    @JsonProperty("path")
    private String path;
}
