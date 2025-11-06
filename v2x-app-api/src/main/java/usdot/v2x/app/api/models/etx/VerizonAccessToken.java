package usdot.v2x.app.api.models.etx;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class VerizonAccessToken {
    @JsonProperty("access_token")
    private String access_token;
    @JsonProperty("token_type")
    private String token_type;
    @JsonProperty("expires_in")
    private String expires_in;
    @JsonProperty("scope")
    private String scope;
}
