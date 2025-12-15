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
@Schema(description = "MQTT ACL entry response")
public class MqttAclResponse {
    @Schema(description = "ACL entry ID", example = "1")
    private Long id;

    @Schema(description = "Client ID", example = "client_001")
    private String clientId;

    @Schema(description = "Topic pattern", example = "/v2x/geohash/+/+/+/+/+/+/+/BSM")
    private String topicPattern;

    @Schema(description = "Access type", example = "readwrite")
    private MqttAclEntry.AccessType accessType;

    @Schema(description = "Whether the ACL entry is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Creation timestamp", example = "2024-01-01T00:00:00Z")
    private Instant createdAt;
}

