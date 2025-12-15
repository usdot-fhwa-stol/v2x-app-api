package usdot.v2x.app.api.mqtt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.mqtt.MqttClientRegistrationRequest;
import usdot.v2x.app.api.models.mqtt.MqttClientResponse;
import usdot.v2x.app.api.models.mqtt.MqttCertificateResponse;
import usdot.v2x.app.api.services.MqttClientService;
import usdot.v2x.app.api.utils.SecurityContextUtils;

import java.util.List;

@RestController
@RequestMapping("/prd/v2/mqtt/clients")
@Tag(name = "MQTT Clients", description = "MQTT client registration and certificate management endpoints")
@RequiredArgsConstructor
@Slf4j
public class MqttClientRestController {
    
    private final MqttClientService mqttClientService;
    private final SecurityContextUtils securityContextUtils;
    
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping
    @Operation(summary = "Register MQTT client", 
            description = "Registers a new MQTT client with the Mosquitto broker. Client ID is auto-generated if not provided. Certificate generation is required for certificate-based authentication.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Client registered successfully")
    @ApiResponse(responseCode = "400", description = "Bad request - invalid client data")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ResponseEntity<MqttClientResponse> registerClient(
            @Valid @RequestBody MqttClientRegistrationRequest request) {
        try {
            String createdBy = securityContextUtils.determineRequestedBy();
            MqttClientResponse response = mqttClientService.registerClient(request, createdBy);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error registering MQTT client", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/{clientId}")
    @Operation(summary = "Get MQTT client", 
            description = "Retrieves MQTT client information by client ID",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Client retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Client not found")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    public ResponseEntity<MqttClientResponse> getClient(
            @Parameter(description = "Client ID", required = true) @PathVariable String clientId) {
        MqttClientResponse response = mqttClientService.getClient(clientId);
        if (response != null) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping
    @Operation(summary = "Get MQTT clients", 
            description = "Retrieves all MQTT clients for the current user",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Clients retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    public ResponseEntity<List<MqttClientResponse>> getClients() {
        String createdBy = securityContextUtils.determineRequestedBy();
        List<MqttClientResponse> clients = mqttClientService.getClientsByUser(createdBy);
        return ResponseEntity.ok(clients);
    }
    
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @DeleteMapping("/{clientId}")
    @Operation(summary = "Delete MQTT client", 
            description = "Deletes (deactivates) an MQTT client",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Client deleted successfully")
    @ApiResponse(responseCode = "404", description = "Client not found")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    public ResponseEntity<Void> deleteClient(
            @Parameter(description = "Client ID", required = true) @PathVariable String clientId) {
        boolean deleted = mqttClientService.deleteClient(clientId);
        if (deleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/{clientId}/certificate")
    @Operation(summary = "Get MQTT client certificate", 
            description = "Retrieves the client certificate bundle (client cert, key, and CA cert) in PEM format",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Certificate retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Client or certificate not found")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    public ResponseEntity<MqttCertificateResponse> getCertificate(
            @Parameter(description = "Client ID", required = true) @PathVariable String clientId) {
        try {
            MqttCertificateResponse response = mqttClientService.getClientCertificate(clientId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error retrieving certificate: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving certificate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

