package usdot.v2x.app.api.models.mqtt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request to register a new MQTT client. Client ID will be auto-generated if not provided.")
public class MqttClientRegistrationRequest {
    @Schema(description = "Optional client identifier. If not provided, a UUID will be auto-generated", example = "client_001")
    private String clientId;

    @Schema(description = "Whether to generate a client certificate. Required for certificate-based authentication.", example = "true", defaultValue = "true")
    private Boolean generateCertificate = true;
}

