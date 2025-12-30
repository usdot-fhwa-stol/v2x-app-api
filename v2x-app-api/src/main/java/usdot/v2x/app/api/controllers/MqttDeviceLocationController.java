package usdot.v2x.app.api.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import usdot.v2x.app.api.models.mqtt.MqttDeviceLocationRequest;
import usdot.v2x.app.api.models.mqtt.MqttDeviceLocationResponse;
import usdot.v2x.app.api.services.MqttDeviceLocationService;

import java.util.Optional;

/**
 * REST controller for MQTT device location registration and management.
 * Used for geo-routing based on MQTT client IDs.
 */
@Slf4j
@RestController
@RequestMapping("/prd/v2/mqtt/devices")
@Tag(name = "MQTT Device Location", description = "MQTT device location registration for geo-routing")
public class MqttDeviceLocationController {

    private final MqttDeviceLocationService locationService;

    @Autowired
    public MqttDeviceLocationController(MqttDeviceLocationService locationService) {
        this.locationService = locationService;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping("/location")
    @Operation(
        summary = "Register or update MQTT device location",
        description = "Registers or updates the geographic location of an MQTT device. " +
                      "This location is used by the geo-router service to route messages to relevant devices.",
        security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Location registered/updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<MqttDeviceLocationResponse> registerLocation(
            @Valid @RequestBody MqttDeviceLocationRequest request) {
        log.info("Registering/updating MQTT device location: clientId={}, lat={}, lon={}",
                request.getMqttClientId(), request.getLatitude(), request.getLongitude());

        MqttDeviceLocationResponse response = locationService.registerOrUpdateLocation(request);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/location/{mqttClientId}")
    @Operation(
        summary = "Get MQTT device location",
        description = "Retrieves the current location of an MQTT device.",
        security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Location found"),
        @ApiResponse(responseCode = "404", description = "Device not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<MqttDeviceLocationResponse> getLocation(
            @PathVariable String mqttClientId) {
        Optional<MqttDeviceLocationResponse> location = locationService.getLocation(mqttClientId);
        
        if (location.isPresent()) {
            return ResponseEntity.ok(location.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @DeleteMapping("/location/{mqttClientId}")
    @Operation(
        summary = "Deactivate MQTT device",
        description = "Deactivates an MQTT device (marks as inactive, typically on disconnect).",
        security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Device deactivated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<Void> deactivateDevice(@PathVariable String mqttClientId) {
        log.info("Deactivating MQTT device: {}", mqttClientId);
        locationService.deactivateDevice(mqttClientId);
        return ResponseEntity.noContent().build();
    }
}



