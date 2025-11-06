package usdot.v2x.app.api.models.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Schema(description = "Request model for authentication token")
public class TokenPostRequest {
    @JsonProperty("username")
    @Schema(description = "Username for authentication", required = true)
    private String username;
    @JsonProperty("password")
    @Schema(description = "Password for authentication", required = true)
    private String password;
}
