package usdot.v2x.app.api.deposit.geofence;

import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentResponse;
import usdot.v2x.app.api.services.GeofenceDeploymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/prd/v2/deposit/geofence/deployments")
@Tag(name = "Geofence Deployments", description = "Geofence deployment management endpoints")
@RequiredArgsConstructor
@Slf4j
public class GeofenceDeploymentRestController {

    private final GeofenceDeploymentService geofenceDeploymentService;

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping
    @Operation(summary = "Get all active Geofence deployments", description = "Retrieves all active Geofence deployments from the database. "
            +
            "This endpoint returns a list of all currently active Geofence deployments " +
            "with their configuration details, geospatial information, and metadata.")
    @ApiResponse(responseCode = "200", description = "Geofence deployments retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<GeofenceDeploymentResponse>> getAllActiveGeofenceDeployments() {
        try {
            List<GeofenceDeploymentResponse> deployments = geofenceDeploymentService.getActiveGeofenceDeployments();
            return ResponseEntity.ok(deployments);
        } catch (Exception e) {
            log.error("Error retrieving all active Geofence deployments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/{geofenceId}")
    @Operation(summary = "Get Geofence deployment by ID", description = "Retrieves a specific Geofence deployment by its Geofence ID. "
            +
            "This endpoint returns the complete configuration and metadata " +
            "for the specified Geofence deployment.")
    @ApiResponse(responseCode = "200", description = "Geofence deployment retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Geofence deployment not found")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<GeofenceDeploymentResponse> getGeofenceDeployment(
            @Parameter(description = "Geofence ID to retrieve", required = true, example = "GEOFENCE_12345") @PathVariable String geofenceId) {
        try {
            GeofenceDeploymentResponse deployment = geofenceDeploymentService.getGeofenceDeployment(geofenceId);
            if (deployment != null) {
                return ResponseEntity.ok(deployment);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error retrieving Geofence deployment with ID: {}", geofenceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/geohash/{geohash}")
    @Operation(summary = "Get Geofence deployments by geohash", description = "Retrieves all Geofence deployments that cover a specific geohash. "
            +
            "This endpoint is useful for finding Geofences relevant to a particular " +
            "geographical area based on geohash precision.")
    @ApiResponse(responseCode = "200", description = "Geofence deployments retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<GeofenceDeploymentResponse>> getGeofenceDeploymentsByGeohash(
            @Parameter(description = "Geohash to search for", required = true, example = "9q8yy") @PathVariable String geohash) {
        try {
            List<GeofenceDeploymentResponse> deployments = geofenceDeploymentService
                    .getGeofenceDeploymentsByGeohash(geohash);
            return ResponseEntity.ok(deployments);
        } catch (Exception e) {
            log.error("Error retrieving Geofence deployments for geohash: {}", geohash, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR')")
    @GetMapping("/user/{username}")
    @Operation(summary = "Get Geofence deployments by user", description = "Retrieves all active Geofence deployments deployed by a specific user. "
            +
            "This endpoint is useful for user-specific Geofence management and auditing.")
    @ApiResponse(responseCode = "200", description = "Geofence deployments retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<GeofenceDeploymentResponse>> getGeofenceDeploymentsByUser(
            @Parameter(description = "Username to filter by", required = true, example = "admin") @PathVariable String username) {
        try {
            List<GeofenceDeploymentResponse> deployments = geofenceDeploymentService
                    .getActiveGeofenceDeploymentsByUser(username);
            return ResponseEntity.ok(deployments);
        } catch (Exception e) {
            log.error("Error retrieving Geofence deployments for user: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR')")
    @GetMapping("/expired")
    @Operation(summary = "Get expired Geofence deployments", description = "Retrieves all Geofence deployments that have expired. "
            +
            "This endpoint is useful for monitoring and debugging expiration issues.")
    @ApiResponse(responseCode = "200", description = "Expired Geofence deployments retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<GeofenceDeploymentResponse>> getExpiredGeofenceDeployments() {
        try {
            List<GeofenceDeploymentResponse> deployments = geofenceDeploymentService
                    .getExpiredGeofenceDeployments();
            return ResponseEntity.ok(deployments);
        } catch (Exception e) {
            log.error("Error retrieving expired Geofence deployments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/{geofenceId}/geohashes")
    @Operation(summary = "Get geohashes for a Geofence deployment", description = "Retrieves all geohashes associated with a specific Geofence deployment. "
            +
            "This endpoint is useful for understanding the geographical coverage " +
            "of a particular Geofence deployment.")
    @ApiResponse(responseCode = "200", description = "Geohashes retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Geofence deployment not found")
    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication")
    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<String>> getGeofenceGeohashes(
            @Parameter(description = "Geofence ID to get geohashes for", required = true, example = "GEOFENCE_12345") @PathVariable String geofenceId) {
        try {
            List<String> geohashes = geofenceDeploymentService.getGeofenceGeohashes(geofenceId);
            return ResponseEntity.ok(geohashes);
        } catch (Exception e) {
            log.error("Error retrieving geohashes for Geofence ID: {}", geofenceId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
