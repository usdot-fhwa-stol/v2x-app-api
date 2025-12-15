package usdot.v2x.app.api.models.mqtt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MQTT client registration response")
public class MqttClientResponse {
    @Schema(description = "Client ID", example = "client_001")
    private String clientId;

    @Schema(description = "Username", example = "mqtt_user")
    private String username;

    @Schema(description = "Whether the client is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Certificate CN if certificate was generated", example = "client_001")
    private String certificateCn;

    @Schema(description = "Certificate expiration date", example = "2025-12-31T23:59:59Z")
    private Instant certificateExpiresAt;

    @Schema(description = "Last connection timestamp", example = "2024-01-15T10:30:00Z")
    private Instant lastConnectedAt;

    @Schema(description = "Creation timestamp", example = "2024-01-01T00:00:00Z")
    private Instant createdAt;
}

