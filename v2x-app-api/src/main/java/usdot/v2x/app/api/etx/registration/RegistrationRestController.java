package usdot.v2x.app.api.etx.registration;

import usdot.v2x.app.api.config.etx.EtxProperties;
import usdot.v2x.app.api.models.etx.ErrorResponse;
import usdot.v2x.app.api.models.etx.ErrorResponseException;
import usdot.v2x.app.api.models.etx.registration.*;
import usdot.v2x.app.api.services.RegistrationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/prd/v2")
@Tag(name = "Registration", description = "ETX client registration and connection management endpoints")
public class RegistrationRestController {
    RegistrationApi registrationApi;

    @Autowired
    private RegistrationLogService registrationLogService;

    @Autowired
    private EtxProperties etxProperties;

    RegistrationRestController(
            RegistrationApi registrationApi) {
        this.registrationApi = registrationApi;
    }

    private String determineVendorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("SecurityContext authentication: {}", authentication);

        if (authentication != null) {
            log.debug("Authentication name: {}", authentication.getName());
            log.debug("Authentication authorities: {}", authentication.getAuthorities());
            log.debug("Authentication is authenticated: {}", authentication.isAuthenticated());

            if (authentication.getAuthorities() != null) {
                boolean isDepositor = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(authority -> "ROLE_DEPOSITOR".equals(authority));

                if (isDepositor) {
                    log.debug("Using depositor vendor ID for ROLE_DEPOSITOR user");
                    return etxProperties.getDepositorVendorId();
                }
            }
        } else {
            log.warn("Authentication is null in SecurityContext");
        }
        log.debug("Using default vendor ID for user");
        return etxProperties.getVendorId();
    }

    private String determineRequestedBy() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("SecurityContext authentication: {}", authentication);

        if (authentication != null) {
            log.debug("Authentication name: {}", authentication.getName());
            log.debug("Authentication authorities: {}", authentication.getAuthorities());
            log.debug("Authentication is authenticated: {}", authentication.isAuthenticated());

            if (authentication.getAuthorities() != null) {
                return authentication.getName();
            }
        } else {
            log.warn("Authentication is null in SecurityContext");
        }
        log.debug("Using default requested by for user");
        return "unknown";
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping(value = "/registration", produces = "application/json")
    @Operation(summary = "Register ETX client", description = "Registers a new ETX client with the system. This endpoint supports retry logic "
            +
            "for handling pending registrations. The client must provide valid client type and subtype " +
            "information for successful registration.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Client registration information", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationPostRequest.class))))
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
        String vendorId = determineVendorId();
        String requestedBy = determineRequestedBy();

        // Check capacity and clean up if needed before registration
        registrationLogService.ensureCapacityForRegistration(vendorId, requestedBy);

        if (retry) {
            return registrationApi.clientRegistrationRetryPending(request, num_retries, num_sec_to_sleep)
                    .doOnSuccess(registrationResponse -> {
                        if (registrationResponse != null) {
                            registrationLogService.logRegistration(
                                    registrationResponse,
                                    request.getClientType(),
                                    request.getClientSubtype(),
                                    vendorId,
                                    requestedBy);
                        }
                    });
        } else {
            return registrationApi.clientRegistrationPost(request)
                    .doOnSuccess(registrationResponse -> {
                        if (registrationResponse != null) {
                            registrationLogService.logRegistration(
                                    registrationResponse,
                                    request.getClientType(),
                                    request.getClientSubtype(),
                                    vendorId,
                                    requestedBy);
                        }
                    });
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PutMapping("/registration")
    @Operation(summary = "Update ETX client registration", description = "Updates an existing ETX client registration. This endpoint supports retry logic "
            +
            "for handling pending registration updates. The client must provide a valid device ID " +
            "for successful registration update.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Client registration update information", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationPutRequest.class))))
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
        String vendorId = determineVendorId();
        String requestedBy = determineRequestedBy();

        // Check capacity and clean up if needed before registration update
        registrationLogService.ensureCapacityForRegistration(vendorId, requestedBy);

        if (retry) {
            return registrationApi.clientRegistrationPutRetryPending(request, num_retries,
                    num_sec_to_sleep);
        } else {
            return registrationApi.clientRegistrationPut(request);
        }
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping("/connection")
    @Operation(summary = "Establish ETX client connection", description = "Establishes a connection for an ETX client with location and network information. "
            +
            "This endpoint is used to register device location and network connectivity for " +
            "V2X communication.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Client connection information", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConnectionPostRequest.class))))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Connection established successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConnectionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid connection data", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions", content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Mono<ConnectionResponse> postConnection(
            @Parameter(description = "Client connection information", required = true) @RequestBody ConnectionPostRequest request) {

        return registrationApi.clientConnectionPost(request)
                .doOnSuccess(connectionResponse -> {
                    // Update last connected timestamp for the device
                    if (connectionResponse != null && request.getDeviceId() != null) {
                        registrationLogService.updateLastConnected(request.getDeviceId());
                    }
                });
    }

    @Deprecated(since = "v1.0.0", forRemoval = true)
    @PreAuthorize("hasRole('ROLE_ADMIN') || hasRole('ROLE_DEPOSITOR') || hasRole('ROLE_USER')")
    @PostMapping("/registration-connection")
    @Operation(summary = "Register and connect ETX client", description = "Performs both client registration and connection establishment in a single operation. "
            +
            "This endpoint first registers the client with the system, then establishes a connection using the returned device ID. "
            +
            "This is a convenience endpoint that combines the functionality of both registration and connection endpoints.", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Combined registration and connection information", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegistrationConnectionPostRequest.class))))
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
        String vendorId = determineVendorId();
        String requestedBy = determineRequestedBy();

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
                                }
                            })
                            .map(connectionResponse -> new CompleteResponse(
                                    registrationResponse, connectionResponse));

                });
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/registration/cleanup/vendor/{vendorId}")
    @Operation(summary = "Clean up old registrations for vendor", description = "Cleans up old registrations for a specific vendor (Admin only)")
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
    @Operation(summary = "Check registration status", description = "Checks the registration status of a device by its device ID against the ETX API")
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
        return registrationApi.checkRegistration(deviceId, determineVendorId())
                .doOnSuccess(registrationCheckResponse -> {
                    // Update last connected timestamp for the device when checking registration
                    if (registrationCheckResponse != null && registrationCheckResponse.getDeviceId() != null) {
                        registrationLogService.updateLastConnected(registrationCheckResponse.getDeviceId());
                    }
                });
    }

}
