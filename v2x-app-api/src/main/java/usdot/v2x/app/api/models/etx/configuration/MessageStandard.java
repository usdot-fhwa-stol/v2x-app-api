package usdot.v2x.app.api.models.etx.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MessageStandard {
    @JsonProperty("etsi")
    ETSI,
    @JsonProperty("sae")
    SAE
}
