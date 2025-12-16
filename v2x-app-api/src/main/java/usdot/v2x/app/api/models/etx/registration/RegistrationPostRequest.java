package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request model for ETX registration")
public class RegistrationPostRequest {
    @JsonProperty("ClientType")
    @Schema(description = "Type of client for registration", required = true)
    private RegistrationClientType clientType;
    @JsonProperty("ClientSubtype")
    @Schema(description = "Subtype of client for registration", required = true)
    private RegistrationClientSubType clientSubtype;
}
