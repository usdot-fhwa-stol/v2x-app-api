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
import usdot.v2x.app.api.models.mqtt.MqttAclRequest;
import usdot.v2x.app.api.models.mqtt.MqttAclResponse;
import usdot.v2x.app.api.services.MqttAclService;
import usdot.v2x.app.api.utils.SecurityContextUtils;

import java.util.List;

@RestController
@RequestMapping("/prd/v2/mqtt/acl")
@Tag(name = "MQTT ACL", description = "MQTT Access Control List management endpoints")
@RequiredArgsConstructor
@Slf4j
public class MqttAclRestController {
    
    private final MqttAclService mqttAclService;
    private final SecurityContextUtils securityContextUtils;
    
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping
    @Operation(summary = "Create or update ACL entry", 
            description = "Creates or updates an ACL entry for an MQTT client. Regenerates the Mosquitto ACL file.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "ACL entry created/updated successfully")
    @ApiResponse(responseCode = "400", description = "Bad request - invalid ACL data")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    public ResponseEntity<MqttAclResponse> createOrUpdateAcl(
            @Valid @RequestBody MqttAclRequest request) {
        try {
            String createdBy = securityContextUtils.determineRequestedBy();
            MqttAclResponse response = mqttAclService.createOrUpdateAcl(request, createdBy);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating/updating ACL entry", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/{clientId}")
    @Operation(summary = "Get ACL entries for client", 
            description = "Retrieves all active ACL entries for a specific MQTT client",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "ACL entries retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    public ResponseEntity<List<MqttAclResponse>> getAclEntries(
            @Parameter(description = "Client ID", required = true) @PathVariable String clientId) {
        List<MqttAclResponse> entries = mqttAclService.getAclEntries(clientId);
        return ResponseEntity.ok(entries);
    }
    
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @DeleteMapping("/{aclId}")
    @Operation(summary = "Delete ACL entry", 
            description = "Deletes (deactivates) an ACL entry by ID. Regenerates the Mosquitto ACL file.",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "ACL entry deleted successfully")
    @ApiResponse(responseCode = "404", description = "ACL entry not found")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    public ResponseEntity<Void> deleteAclEntry(
            @Parameter(description = "ACL entry ID", required = true) @PathVariable Long aclId) {
        boolean deleted = mqttAclService.deleteAclEntry(aclId);
        if (deleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/regenerate")
    @Operation(summary = "Regenerate ACL file", 
            description = "Manually triggers regeneration of the Mosquitto ACL file from database entries",
            security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "ACL file regenerated successfully")
    @ApiResponse(responseCode = "500", description = "Failed to regenerate ACL file")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    public ResponseEntity<Void> regenerateAclFile() {
        boolean success = mqttAclService.regenerateAclFile();
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

