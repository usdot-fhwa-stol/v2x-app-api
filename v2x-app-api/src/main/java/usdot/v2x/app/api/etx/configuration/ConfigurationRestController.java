package usdot.v2x.app.api.etx.configuration;

import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofence;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofenceResponse;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationGeofenceSummary;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.configuration.ConfigurationClearGeofence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

// WARNING: DO NOT USE io.swagger.v3.oas.annotations.parameters.RequestBody IN THIS FILE IT CAUSES REQUEST BODY PARSING ISSUES
// import io.swagger.v3.oas.annotations.parameters.RequestBody;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import static org.springframework.http.MediaType.*;

import java.util.List;

/**
 * REST controller for ETX configuration management operations.
 */
@RestController
@RequestMapping("/prd/v2/configurations")
public class ConfigurationRestController {
    ConfigurationApi configurationApi;

    ConfigurationRestController(
            @Autowired(required = false) ConfigurationApi configurationApi) {
        this.configurationApi = configurationApi;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/geofence/ids")
    @Operation(summary = "Get all geofence IDs", description = "Retrieves a list of all available geofence IDs in the ETX. "
            +
            "This endpoint returns summary information for all configured geofences in the ETX " +
            "without the full geofence details.", security = @SecurityRequirement(name = "BearerAuth"), responses = {
                    @ApiResponse(responseCode = "200", description = "List of geofence summaries retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigurationGeofenceSummary.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Mono<List<ConfigurationGeofenceSummary>> getGeofences() {
        return configurationApi.getGeofences();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/geofence")
    @Operation(summary = "Get geofence by ID", description = "Retrieves detailed information for a specific geofence by its ID. "
            +
            "This endpoint returns the complete geofence configuration including " +
            "geometry, properties, and metadata.", security = @SecurityRequirement(name = "BearerAuth"), parameters = {
                    @Parameter(name = "id", description = "Unique identifier of the geofence", required = true, example = "geofence-001")
            }, responses = {
                    @ApiResponse(responseCode = "200", description = "Geofence details retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigurationGeofenceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid geofence ID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Not found - geofence not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Mono<ConfigurationGeofenceResponse> getGeofence(
            @Parameter(description = "Unique identifier of the geofence", required = true, example = "geofence-001") @RequestParam(name = "id") String id) {
        return configurationApi.getGeofence(id);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/geofence")
    @Operation(summary = "Create new geofence", description = "Creates a new geofence with the specified configuration. "
            +
            "The geofence can be used for V2X traffic management and safety applications.", security = @SecurityRequirement(name = "BearerAuth"), requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Geofence configuration to create", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigurationGeofence.class))), responses = {
                    @ApiResponse(responseCode = "200", description = "Geofence created successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigurationGeofenceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid geofence configuration", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Mono<ConfigurationGeofenceResponse> createGeofence(
            @Parameter(description = "Geofence configuration to create", required = true) @RequestBody ConfigurationGeofence geofence) {
        return configurationApi.createGeofence(geofence);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PutMapping("/geofence")
    @Operation(summary = "Update existing geofence", description = "Updates an existing geofence with new configuration. "
            +
            "All fields in the request will replace the existing geofence configuration.", security = @SecurityRequirement(name = "BearerAuth"), parameters = {
                    @Parameter(name = "id", description = "Unique identifier of the geofence to update", required = true, example = "geofence-001")
            }, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated geofence configuration", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigurationGeofence.class))), responses = {
                    @ApiResponse(responseCode = "200", description = "Geofence updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigurationGeofenceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid geofence configuration", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Not found - geofence not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Mono<ConfigurationGeofenceResponse> updateGeofence(
            @Parameter(description = "Unique identifier of the geofence to update", required = true, example = "geofence-001") @RequestParam(name = "id") String id,
            @Parameter(description = "Updated geofence configuration", required = true) @RequestBody ConfigurationGeofence geofence) {
        return configurationApi.updateGeofence(id, geofence);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/geofence")
    @Operation(summary = "Delete geofence", description = "Deletes a geofence from the system. This operation is irreversible "
            +
            "and will remove all associated configuration and data.", security = @SecurityRequirement(name = "BearerAuth"), parameters = {
                    @Parameter(name = "id", description = "Unique identifier of the geofence to delete", required = true, example = "geofence-001")
            }, responses = {
                    @ApiResponse(responseCode = "200", description = "Geofence deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid geofence ID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Not found - geofence not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public Mono<Void> deleteGeofence(
            @Parameter(description = "Unique identifier of the geofence to delete", required = true, example = "geofence-001") @RequestParam(name = "id") String id) {
        return configurationApi.deleteGeofence(id);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR')")
    @PostMapping(value = "/clear", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Clear multiple geofences", description = "Clears deployed geofences from the ETX. If set to only TIM messages it will clear all inactive TIMs."
            +
            "This operation can be used to remove multiple geofences at once.", security = @SecurityRequirement(name = "BearerAuth"), requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Criteria for clearing geofences", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConfigurationClearGeofence.class))), responses = {
                    @ApiResponse(responseCode = "200", description = "Geofences cleared successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = String.class)))),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid clear criteria", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<List<String>> clearGeofences(
            @Parameter(description = "Criteria for clearing geofences", required = true) @RequestBody ConfigurationClearGeofence request) {
        return configurationApi.clearGeofences(request).block();
    }
}
