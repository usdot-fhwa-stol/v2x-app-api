package usdot.v2x.app.api.deposit;

import com.fasterxml.jackson.core.JsonProcessingException;
import usdot.v2x.app.api.models.etx.configuration.DepositRequest;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentRequest;
import usdot.v2x.app.api.models.geofence.GeofenceDeploymentResponse;
import usdot.v2x.app.api.services.GeofenceDeploymentService;
import usdot.v2x.app.api.utils.GeofenceDeploymentConverter;
import usdot.v2x.app.api.config.DepositProperties;
import usdot.v2x.app.api.config.GeofenceProperties;
import usdot.v2x.app.api.etx.configuration.ConfigurationApi;
import usdot.v2x.app.api.models.etx.ErrorResponseException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.MediaType.*;

import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/prd/v2/deposit")
@Tag(name = "Deposit", description = "V2X Message Deposit endpoints for deployment to the ETX MQTT Broker")
@Slf4j
public class DepositRestController {
    private final GeofenceDeploymentService geofenceDeploymentService;
    private final GeofenceDeploymentConverter geofenceDeploymentConverter;
    private final GeofenceProperties geofenceProperties;
    private final DepositProperties depositProperties;
    private final ConfigurationApi configurationApi;

    DepositRestController(
            GeofenceDeploymentService geofenceDeploymentService,
            GeofenceDeploymentConverter geofenceDeploymentConverter,
            GeofenceProperties geofenceProperties,
            DepositProperties depositProperties,
            ConfigurationApi configurationApi) {
        this.geofenceDeploymentService = geofenceDeploymentService;
        this.geofenceDeploymentConverter = geofenceDeploymentConverter;
        this.geofenceProperties = geofenceProperties;
        this.depositProperties = depositProperties;
        this.configurationApi = configurationApi;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR')")
    @PostMapping(value = "/geofence", consumes = APPLICATION_JSON_VALUE)
    @Operation(summary = "Deposit V2X Message", description = "Deposits V2X message data to the database. "
            +
            "This endpoint is used to deploy V2X messages (TIM, MAP, etc.) with geospatial information. " +
            "The configuration must include geofence overrides to define the deployment area. " +
            "Vendor ID is automatically determined from the user's JWT token based on their role.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Configuration data to deposit", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepositRequest.class))), responses = {
                    @ApiResponse(responseCode = "200", description = "V2X message deployed successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = GeofenceDeploymentResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid configuration data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "409", description = "Conflict - no available geohash or conflicting state", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "413", description = "Payload too large - exceeds maximum allowed geohashes", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Unprocessable entity - invalid ASN.1 data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> deposit(
            @Parameter(description = "V2X message data to deposit", required = true) @RequestBody DepositRequest request)
            throws JsonProcessingException {
        try {

            if (depositProperties.getMode() == DepositProperties.Mode.ETX_CONFIGURATION_API) {
                // Deposit the V2X message to the ETX Configuration API
                configurationApi.deposit(request);
            } else if (depositProperties.getMode() == DepositProperties.Mode.GEOFENCE_MQTT) {
                // Convert the configuration request to Geofence deployment request
                GeofenceDeploymentRequest geofenceRequest = geofenceDeploymentConverter
                        .convertToGeofenceDeploymentRequest(request);
            } else {
                throw new ErrorResponseException(
                        new ErrorResponse("INVALID_DEPOSIT_MODE", "Invalid deposit mode"),
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // Convert the configuration request to Geofence deployment request
            GeofenceDeploymentRequest geofenceRequest = geofenceDeploymentConverter
                    .convertToGeofenceDeploymentRequest(request);

            // Enforce maximum geohashes per deployment
            int maxGeohashes = geofenceProperties.getLimits().getMaxGeohashes();
            if (geofenceRequest.getGeohashes() != null && geofenceRequest.getGeohashes().size() > maxGeohashes) {
                ErrorResponse errorResponse = new ErrorResponse(
                        "GEOFENCE_TOO_LARGE",
                        "Deposit rejected: exceeds maximum allowed geohashes (" + maxGeohashes + ")");
                return ResponseEntity.status(413).body(errorResponse);
            }

            // Create the Geofence deployment
            GeofenceDeploymentResponse response = geofenceDeploymentService.createGeofenceDeployment(geofenceRequest);

            return ResponseEntity.ok(response);
        } catch (ErrorResponseException ex) {
            log.error("An error response exception occurred while processing the request: {}", ex.getMessage(), ex);
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getErrorResponse());
        } catch (IllegalArgumentException e) {
            log.error("An invalid ASN.1 data error occurred while processing the request: {}", e.getMessage(), e);
            // Handle validation errors (422 - Unprocessable Entity)
            ErrorResponse errorResponse = new ErrorResponse("INVALID_ASN_DATA",
                    "Invalid ASN.1 data: " + e.getMessage());
            return ResponseEntity.status(422).body(errorResponse);
        } catch (Exception e) {
            log.error("An internal server error occurred while processing the request: {}", e.getMessage(), e);
            // Handle internal server errors (500)
            ErrorResponse errorResponse = new ErrorResponse("INTERNAL_SERVER_ERROR",
                    "An internal server error occurred while processing the request");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR')")
    @DeleteMapping("/geofence")
    @Operation(summary = "Delete geofence by identifier", description = "Deletes geofence deployments associated with a specific identifier. "
            +
            "This endpoint allows for manual removal of geofence deployments by their identifier. "
            +
            "The system will deactivate the geofence deployment by setting is_active to false. "
            +
            "This follows REST best practices by using query parameters for resource identification.", responses = {
                    @ApiResponse(responseCode = "204", description = "Geofence deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Bad request - invalid identifier", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "404", description = "Not found - no geofence found with the specified identifier", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<?> deleteGeofence(
            @Parameter(description = "Geofence identifier to delete", required = true, example = "GEOFENCE_12345") @RequestParam("identifier") String identifier) {
        try {
            // Validate identifier
            if (identifier == null || identifier.trim().isEmpty()) {
                ErrorResponse errorResponse = new ErrorResponse("INVALID_IDENTIFIER",
                        "Identifier cannot be null or empty");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Remove any leading/trailing whitespace
            String cleanIdentifier = identifier.trim();

            // Deactivate the geofence deployment
            boolean deactivated = geofenceDeploymentService.deactivateGeofenceDeployment(cleanIdentifier);

            if (!deactivated) {
                ErrorResponse errorResponse = new ErrorResponse("GEOFENCE_NOT_FOUND",
                        "No geofence found with the specified identifier");
                return ResponseEntity.status(404).body(errorResponse);
            }

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            ErrorResponse errorResponse = new ErrorResponse("INTERNAL_SERVER_ERROR",
                    "An internal server error occurred while processing the request");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
