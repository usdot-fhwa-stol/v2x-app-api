package usdot.v2x.app.api.etx.registration;

import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.registration.*;
import usdot.v2x.app.api.services.RegistrationEventService;
import usdot.v2x.app.api.services.RegistrationLogService;
import usdot.v2x.app.api.utils.SecurityContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// WARNING: DO NOT USE io.swagger.v3.oas.annotations.parameters.RequestBody IN THIS FILE IT CAUSES REQUEST BODY PARSING ISSUES
// import io.swagger.v3.oas.annotations.parameters.RequestBody;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * REST controller for ETX client registration and connection management.
 */
@Slf4j
@RestController
@RequestMapping("/prd/v2")
@Tag(name = "Registration", description = "ETX client registration and connection management endpoints")
public class RegistrationRestController {
    RegistrationApi registrationApi;

    @Autowired
    private RegistrationLogService registrationLogService;

    @Autowired
    private SecurityContextUtils securityContextUtils;

    @Autowired
    private RegistrationEventService registrationEventService;

    RegistrationRestController(
            RegistrationApi registrationApi) {
        this.registrationApi = registrationApi;
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping(value = "/registration", produces = "application/json")
    @Operation(summary = "Register ETX client", description = "Registers a new ETX client with the system. This endpoint supports retry logic "
            +
            "for handling pending registrations. The client must provide valid client type and subtype " +
            "information for successful registration.", security = @SecurityRequirement(name = "BearerAuth"), requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Client registration information", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationPostRequest.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid registration data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<RegistrationResponse> postRegistration(
            @Parameter(description = "Client registration information", required = true) @RequestBody RegistrationPostRequest request,
            @Parameter(description = "Enable retry logic for pending registrations", example = "true") @RequestParam(name = "retry", required = false, defaultValue = "true") boolean retry,
            @Parameter(description = "Number of retry attempts for pending registrations", example = "5") @RequestParam(name = "num_retries", required = false, defaultValue = "5") Integer num_retries,
            @Parameter(description = "Seconds to sleep between retry attempts", example = "15") @RequestParam(name = "num_sec_to_sleep", required = false, defaultValue = "15") Integer num_sec_to_sleep) {

        // Determine vendor ID before making reactive calls
        String vendorId = securityContextUtils.determineVendorId();
        String requestedBy = securityContextUtils.determineRequestedBy();

        // Check capacity and clean up if needed before registration
        registrationLogService.ensureCapacityForRegistration(vendorId, requestedBy);

        Mono<RegistrationResponse> registration = retry
                ? registrationApi.clientRegistrationRetryPending(request, num_retries, num_sec_to_sleep)
                : registrationApi.clientRegistrationPost(request);

        return registration
                .doOnSuccess(registrationResponse -> {
                    if (registrationResponse != null) {
                        registrationLogService.logRegistration(
                                registrationResponse,
                                request.getClientType(),
                                request.getClientSubtype(),
                                vendorId,
                                requestedBy);
                    }
                })
                .doOnError(error -> registrationEventService.recordFailure(
                        RegistrationEventType.REGISTER_FAILED, null, vendorId, requestedBy, error));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PutMapping("/registration")
    @Operation(summary = "Update ETX client registration", description = "Updates an existing ETX client registration. This endpoint supports retry logic "
            +
            "for handling pending registration updates. The client must provide a valid device ID " +
            "for successful registration update.", security = @SecurityRequirement(name = "BearerAuth"), requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Client registration update information", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationPutRequest.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration update successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid update data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Not found - device not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<RegistrationResponse> putRegistration(
            @Parameter(description = "Enable retry logic for pending registration updates", example = "true") @RequestParam(name = "retry", required = false, defaultValue = "true") boolean retry,
            @Parameter(description = "Number of retry attempts for pending updates", example = "5") @RequestParam(name = "num_retries", required = false, defaultValue = "5") Integer num_retries,
            @Parameter(description = "Seconds to sleep between retry attempts", example = "15") @RequestParam(name = "num_sec_to_sleep", required = false, defaultValue = "15") Integer num_sec_to_sleep,
            @Parameter(description = "Client registration update information", required = true) @RequestBody RegistrationPutRequest request) {

        // Determine vendor ID before making reactive calls
        String vendorId = securityContextUtils.determineVendorId();
        String requestedBy = securityContextUtils.determineRequestedBy();

        // An update renews an existing registration, so no capacity check or eviction
        Mono<RegistrationResponse> update = retry
                ? registrationApi.clientRegistrationPutRetryPending(request, num_retries, num_sec_to_sleep)
                : registrationApi.clientRegistrationPut(request);

        return update
                .doOnSuccess(registrationResponse -> {
                    if (registrationResponse != null) {
                        registrationLogService.logRegistrationUpdate(
                                registrationResponse, request.getDeviceId(), vendorId, requestedBy);
                    }
                })
                .doOnError(error -> registrationEventService.recordFailure(
                        RegistrationEventType.PUT_FAILED, request.getDeviceId(), vendorId, requestedBy, error));
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping("/connection")
    @Operation(summary = "Establish ETX client connection", description = "Establishes a connection for an ETX client with location and network information. "
            +
            "This endpoint is used to register device location and network connectivity for " +
            "V2X communication.", security = @SecurityRequirement(name = "BearerAuth"), requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Client connection information", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConnectionPostRequest.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Connection established successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConnectionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid connection data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ConnectionResponse> postConnection(
            @Parameter(description = "Client connection information", required = true) @RequestBody ConnectionPostRequest request) {

        // Determine vendor ID and requested by before making reactive calls
        String vendorId = securityContextUtils.determineVendorId();
        String requestedBy = securityContextUtils.determineRequestedBy();

        return registrationApi.clientConnectionPost(request)
                .doOnSuccess(connectionResponse -> {
                    // Update last connected timestamp for the device
                    if (connectionResponse != null && request.getDeviceId() != null) {
                        registrationLogService.updateLastConnected(request.getDeviceId());
                        recordConnect(request.getDeviceId(), vendorId, requestedBy, connectionResponse);
                    }
                })
                .doOnError(error -> registrationEventService.recordFailure(
                        RegistrationEventType.CONNECT_FAILED, request.getDeviceId(), vendorId, requestedBy, error));
    }

    @Deprecated(since = "v1.0.0", forRemoval = true)
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping("/registration-connection")
    @Operation(summary = "Register and connect ETX client", description = "Performs both client registration and connection establishment in a single operation. "
            +
            "This endpoint first registers the client with the system, then establishes a connection using the returned device ID. "
            +
            "This is a convenience endpoint that combines the functionality of both registration and connection endpoints.", security = @SecurityRequirement(name = "BearerAuth"), requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Combined registration and connection information", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationConnectionPostRequest.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration and connection successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CompleteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid registration or connection data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal server error - registration or connection failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<CompleteResponse> postRegistrationConnection(
            @Parameter(description = "Number of retry attempts for pending registrations", example = "5") @RequestParam(name = "num_retries", required = false, defaultValue = "5") Integer num_retries,
            @Parameter(description = "Seconds to sleep between retry attempts", example = "15") @RequestParam(name = "num_sec_to_sleep", required = false, defaultValue = "15") Integer num_sec_to_sleep,
            @Parameter(description = "Combined registration and connection information", required = true) @RequestBody RegistrationConnectionPostRequest request) {

        // Determine vendor ID and requested by before making reactive calls
        String vendorId = securityContextUtils.determineVendorId();
        String requestedBy = securityContextUtils.determineRequestedBy();

        // Check capacity and clean up if needed before registration
        registrationLogService.ensureCapacityForRegistration(vendorId, requestedBy);

        return registrationApi
                .clientRegistrationRetryPending(request.getRegistrationPostRequest(), num_retries,
                        num_sec_to_sleep)
                .flatMap(registrationResponse -> {
                    if (registrationResponse == null) {
                        return Mono.error(new ErrorResponseException(
                                new ErrorResponse("Failed to register device",
                                        "unknown error"),
                                HttpStatus.INTERNAL_SERVER_ERROR));
                    }

                    // Log the registration to database
                    registrationLogService.logRegistration(
                            registrationResponse,
                            request.getRegistrationPostRequest().getClientType(),
                            request.getRegistrationPostRequest().getClientSubtype(),
                            vendorId,
                            requestedBy);

                    return registrationApi.clientConnectionPost(
                            request.getConnectionPostRequest(
                                    registrationResponse.getDeviceId()))
                            .doOnSuccess(connectionResponse -> {
                                // Update last connected timestamp for the device
                                if (connectionResponse != null && registrationResponse.getDeviceId() != null) {
                                    registrationLogService.updateLastConnected(registrationResponse.getDeviceId());
                                    recordConnect(registrationResponse.getDeviceId(), vendorId, requestedBy,
                                            connectionResponse);
                                }
                            })
                            .doOnError(error -> registrationEventService.recordFailure(
                                    RegistrationEventType.CONNECT_FAILED, registrationResponse.getDeviceId(),
                                    vendorId, requestedBy, error))
                            .map(connectionResponse -> new CompleteResponse(
                                    registrationResponse, connectionResponse));

                });
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/registration/cleanup/vendor/{vendorId}")
    @Operation(summary = "Clean up old registrations for vendor", description = "Cleans up old registrations for a specific vendor (Admin only)", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registrations cleanup completed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<String>> cleanupVendorRegistrations(@PathVariable("vendorId") String vendorId) {
        try {
            List<String> removedDeviceIds = registrationLogService.cleanupOldRegistrationsByVendor(vendorId);
            return ResponseEntity.ok(removedDeviceIds);
        } catch (Exception e) {
            log.error("Failed to cleanup registrations for vendor {}: {}", vendorId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/registration")
    @Operation(summary = "Check registration status", description = "Checks the registration status of a device by its device ID against the ETX API", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration status retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid device ID"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Not found - device not registered"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public Mono<RegistrationCheckResponse> checkRegistration(
            @Parameter(description = "Device ID to check registration status for", required = true, example = "f7306888-94c9-478b-a076-bba9378563a9") @RequestParam("DeviceID") String deviceId) {
        return registrationApi.checkRegistration(deviceId, securityContextUtils.determineVendorId())
                .doOnSuccess(registrationCheckResponse -> {
                    // Update last connected timestamp for the device when checking registration
                    if (registrationCheckResponse != null && registrationCheckResponse.getDeviceId() != null) {
                        registrationLogService.updateLastConnected(registrationCheckResponse.getDeviceId());
                    }
                });
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/acl-rules")
    @Operation(summary = "Get device roles/ACLs", description = "Retrieves device roles/ACLs for the vendor from Thingspace", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Device roles retrieved successfully", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<Object> getAcls() {
        return registrationApi.getDeviceRoles();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @GetMapping("/registration/active")
    @Operation(summary = "List active ETX registrations", description = "Lists the clients currently registered in ETX for the caller's vendor, "
            + "merged with local tracking data. Also reports devices registered in ETX that are not tracked locally "
            + "and local registrations that ETX no longer reports. Admins may pass vendorId to view another vendor.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active registrations retrieved successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ActiveRegistrationsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions or vendorId override without admin role", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ActiveRegistrationsResponse> getActiveRegistrations(
            @Parameter(description = "Vendor ID to query (admin only, defaults to the caller's vendor)") @RequestParam(name = "vendorId", required = false) String vendorId) {
        String callerVendorId = securityContextUtils.determineVendorId();
        if (vendorId != null && !vendorId.equals(callerVendorId) && !securityContextUtils.isAdmin()) {
            return Mono.error(new ErrorResponseException(
                    new ErrorResponse("Forbidden", "Only admins may query another vendor's registrations"),
                    HttpStatus.FORBIDDEN));
        }
        return registrationLogService.getActiveRegistrationsView(vendorId != null ? vendorId : callerVendorId);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping("/registration/reconcile")
    @Operation(summary = "Reconcile registrations with ETX", description = "Marks local registrations that ETX no longer reports as inactive and "
            + "reports ETX registrations that are not tracked locally (Admin only). Defaults to the caller's vendor.", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reconciliation completed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReconcileResult.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error - ETX query failed")
    })
    public ResponseEntity<ReconcileResult> reconcileRegistrations(
            @Parameter(description = "Vendor ID to reconcile (defaults to the caller's vendor)") @RequestParam(name = "vendorId", required = false) String vendorId) {
        String targetVendorId = vendorId != null ? vendorId : securityContextUtils.determineVendorId();
        try {
            return ResponseEntity.ok(registrationLogService.reconcileWithEtx(targetVendorId));
        } catch (Exception e) {
            log.error("Failed to reconcile registrations for vendor {}: {}", targetVendorId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/registration/events")
    @Operation(summary = "List registration events", description = "Lists registration lifecycle events (register, connect, evict, expire, "
            + "reconcile, failures), newest first, to correlate registration activity with message loss (Admin only).", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = RegistrationEvent.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions")
    })
    public List<RegistrationEvent> getRegistrationEvents(
            @Parameter(description = "Filter by device ID") @RequestParam(name = "deviceId", required = false) String deviceId,
            @Parameter(description = "Filter by vendor ID") @RequestParam(name = "vendorId", required = false) String vendorId,
            @Parameter(description = "Start of the window, ISO-8601 (default: 24 hours ago)", example = "2026-10-01T00:00:00Z") @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "End of the window, ISO-8601 (default: now)", example = "2026-10-02T00:00:00Z") @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "Maximum number of events to return (1-5000)", example = "500") @RequestParam(name = "limit", required = false, defaultValue = "500") Integer limit) {
        Instant windowEnd = to != null ? to : Instant.now();
        Instant windowStart = from != null ? from : windowEnd.minus(24, ChronoUnit.HOURS);
        int boundedLimit = Math.max(1, Math.min(limit, 5000));
        return registrationEventService.findEvents(deviceId, vendorId, windowStart, windowEnd, boundedLimit);
    }

    private void recordConnect(String deviceId, String vendorId, String requestedBy,
            ConnectionResponse connectionResponse) {
        registrationEventService.record(RegistrationEventType.CONNECT, deviceId, vendorId, requestedBy,
                "connection established", null, "mqttUrl=" + connectionResponse.getMqttUrl());
    }

}
