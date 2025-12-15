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
@Schema(description = "MQTT client certificate response")
public class MqttCertificateResponse {
    @Schema(description = "Client ID", example = "client_001")
    private String clientId;

    @Schema(description = "Client certificate in PEM format", example = "-----BEGIN CERTIFICATE-----\n...")
    private String clientCertificate;

    @Schema(description = "Client private key in PEM format", example = "-----BEGIN PRIVATE KEY-----\n...")
    private String clientKey;

    @Schema(description = "CA certificate in PEM format", example = "-----BEGIN CERTIFICATE-----\n...")
    private String caCertificate;

    @Schema(description = "Certificate expiration date", example = "2025-12-31T23:59:59Z")
    private Instant expiresAt;

    @Schema(description = "Certificate serial number", example = "1234567890")
    private String serialNumber;
}

