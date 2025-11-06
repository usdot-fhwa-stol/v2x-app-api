package usdot.v2x.app.api.models.etx;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

public @Data class SessionToken {
    @JsonProperty("sessionToken")
    private String sessionToken;
}
