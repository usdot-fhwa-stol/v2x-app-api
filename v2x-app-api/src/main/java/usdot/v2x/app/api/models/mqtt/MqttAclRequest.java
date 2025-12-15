package usdot.v2x.app.api.models.mqtt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to create or update an ACL entry")
public class MqttAclRequest {
    @NotBlank(message = "Client ID is required")
    @Schema(description = "Client ID", example = "client_001", required = true)
    private String clientId;

    @NotBlank(message = "Topic pattern is required")
    @Schema(description = "MQTT topic pattern (supports wildcards: + for single level, # for multi-level)", 
            example = "/v2x/geohash/+/+/+/+/+/+/+/BSM", required = true)
    private String topicPattern;

    @NotNull(message = "Access type is required")
    @Schema(description = "Access type", example = "readwrite", required = true, 
            allowableValues = {"read", "write", "readwrite"})
    private MqttAclEntry.AccessType accessType;
}

