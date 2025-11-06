package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

public @Data class Certificate {
    @JsonProperty("ExpirationTime")
    private String expirationTime;
    @JsonProperty("cert.pem")
    private String cert_pem;
    @JsonProperty("key.pem")
    private String key_pem;
    @JsonProperty("ca.pem")
    private String ca_pem;
}
