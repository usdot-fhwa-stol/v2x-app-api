package usdot.v2x.app.api.deposit.geofence;

import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentResponse;
import usdot.v2x.app.api.services.GeofenceDeploymentService;
import usdot.v2x.app.api.services.GeofenceExpirationCleanupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for geofence expiration management operations.
 */
@RestController
@RequestMapping("/prd/v2/deposit/geofence/expiration")
@Tag(name = "Geofence Expiration", description = "Geofence expiration management endpoints")
public class GeofenceExpirationRestController {

    private final GeofenceDeploymentService geofenceDeploymentService;
    private final GeofenceExpirationCleanupService geofenceExpirationCleanupService;

    public GeofenceExpirationRestController(
            GeofenceDeploymentService geofenceDeploymentService,
            GeofenceExpirationCleanupService geofenceExpirationCleanupService) {
        this.geofenceDeploymentService = geofenceDeploymentService;
        this.geofenceExpirationCleanupService = geofenceExpirationCleanupService;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/cleanup")
    @Operation(summary = "Manually trigger Geofence expiration cleanup", description = "Manually triggers the cleanup process to deactivate expired Geofence deployments. "
            +
            "This endpoint is useful for testing and immediate cleanup without waiting for the scheduled task.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Cleanup completed successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Map<String, Object>> triggerCleanup() {
        try {
            int deactivatedCount = geofenceDeploymentService.deactivateExpiredGeofenceDeployments();

            Map<String, Object> response = new HashMap<>();
            response.put("deactivatedCount", deactivatedCount);
            response.put("message", String.format(
                    "Cleanup completed successfully. %d expired Geofence deployments deactivated.", deactivatedCount));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to cleanup expired Geofence deployments");
            response.put("description", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/expired")
    @Operation(summary = "Get expired Geofence deployments", description = "Retrieves a list of all Geofence deployments that have expired. "
            +
            "This endpoint is useful for monitoring and debugging expiration issues.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Expired Geofence deployments retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<GeofenceDeploymentResponse>> getExpiredGeofenceDeployments() {
        try {
            List<GeofenceDeploymentResponse> expiredDeployments = geofenceDeploymentService
                    .getExpiredGeofenceDeployments();
            return ResponseEntity.ok(expiredDeployments);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/cleanup/status")
    @Operation(summary = "Get cleanup service status", description = "Retrieves the current status of the Geofence expiration cleanup service, "
            +
            "including the cleanup interval and whether the service is enabled.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponse(responseCode = "200", description = "Geofence cleanup service status retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Map<String, Object>> getCleanupStatus() {
        try {
            int intervalMinutes = geofenceExpirationCleanupService.getIntervalMinutes();
            int gracePeriodHours = geofenceExpirationCleanupService.getGracePeriodHours();

            Map<String, Object> status = new HashMap<>();
            status.put("enabled", true);
            status.put("intervalMinutes", intervalMinutes);
            status.put("gracePeriodHours", gracePeriodHours);
            status.put("nextCleanupInMinutes", intervalMinutes);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to retrieve cleanup service status");
            response.put("description", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
