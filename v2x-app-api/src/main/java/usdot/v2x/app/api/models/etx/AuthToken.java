package usdot.v2x.app.api.models.etx;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class AuthToken {
    @JsonProperty("access_token")
    private String access_token;
    @JsonProperty("expires_in")
    private Integer expires_in;
    @JsonProperty("refresh_expires_in")
    private Integer refresh_expires_in;
    @JsonProperty("refresh_token")
    private String refresh_token;
    @JsonProperty("token_type")
    private String token_type;
    @JsonProperty("id_token")
    private String id_token;
    @JsonProperty("not_before_policy")
    private Integer not_before_policy;
    @JsonProperty("scope")
    private String scope;
}
