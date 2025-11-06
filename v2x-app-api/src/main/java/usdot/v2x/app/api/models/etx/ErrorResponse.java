package usdot.v2x.app.api.models.etx;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public @Data class ErrorResponse {
    @JsonProperty("error")
    private String error;
    @JsonProperty("description")
    private String description;
}
