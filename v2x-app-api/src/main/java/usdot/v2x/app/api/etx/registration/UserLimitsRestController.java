package usdot.v2x.app.api.etx.registration;

import usdot.v2x.app.api.models.UserLimits;
import usdot.v2x.app.api.models.dto.UserLimitsRequest;
import usdot.v2x.app.api.models.dto.UserLimitsResponse;
import usdot.v2x.app.api.services.UserLimitsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for user limits management operations.
 */
@Slf4j
@RestController
@RequestMapping("/prd/v2/admin/user-limits")
@Tag(name = "User Limits Management", description = "Endpoints for managing user registration limits")
public class UserLimitsRestController {

    @Autowired
    private UserLimitsService userLimitsService;

    /**
     * Get all user limits
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    @Operation(summary = "Get all user limits", description = "Retrieves all user limits (Admin only)", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User limits retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<UserLimitsResponse>> getAllUserLimits() {
        try {
            List<UserLimits> userLimits = userLimitsService.getAllUserLimits();
            List<UserLimitsResponse> responses = userLimits.stream()
                    .map(UserLimitsResponse::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Failed to get all user limits: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user limits by username
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/user/{username}")
    @Operation(summary = "Get user limits by username", description = "Retrieves user limits for a specific username (Admin only)", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User limits retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<UserLimitsResponse>> getUserLimitsByUsername(
            @Parameter(description = "Username to get limits for", required = true, example = "john.doe") @PathVariable("username") String username) {
        try {
            List<UserLimits> userLimits = userLimitsService.getUserLimitsByUsername(username);
            List<UserLimitsResponse> responses = userLimits.stream()
                    .map(UserLimitsResponse::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Failed to get user limits for username {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user limits by vendor ID
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/vendor/{vendorId}")
    @Operation(summary = "Get user limits by vendor ID", description = "Retrieves user limits for a specific vendor (Admin only)", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User limits retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<UserLimitsResponse>> getUserLimitsByVendorId(
            @Parameter(description = "Vendor ID to get limits for", required = true, example = "NeaeraEval02") @PathVariable("vendorId") String vendorId) {
        try {
            List<UserLimits> userLimits = userLimitsService.getUserLimitsByVendorId(vendorId);
            List<UserLimitsResponse> responses = userLimits.stream()
                    .map(UserLimitsResponse::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Failed to get user limits for vendor {}: {}", vendorId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create or update user limits
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    @Operation(summary = "Create or update user limits", description = "Creates new user limits or updates existing ones. "
            +
            "Sets personal registration limits for a specific user (Admin only)", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User limits created/updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserLimitsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<UserLimitsResponse> createOrUpdateUserLimits(
            @Parameter(description = "User limits information", required = true) @Valid @RequestBody UserLimitsRequest request) {
        try {
            String updatedBy = getCurrentUsername();
            UserLimits userLimits = userLimitsService.createOrUpdateUserLimits(
                    request.getUsername(),
                    request.getVendorId(),
                    request.getMaxRegistrations(),
                    updatedBy);
            UserLimitsResponse response = UserLimitsResponse.fromEntity(userLimits);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create/update user limits: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete user limits
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/user/{username}/vendor/{vendorId}")
    @Operation(summary = "Delete user limits", description = "Deletes user limits for a specific user and vendor (Admin only)", security = @SecurityRequirement(name = "BearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User limits deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "User limits not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteUserLimits(
            @Parameter(description = "Username to delete limits for", required = true, example = "john.doe") @PathVariable("username") String username,
            @Parameter(description = "Vendor ID to delete limits for", required = true, example = "NeaeraEval02") @PathVariable("vendorId") String vendorId) {
        try {
            String deletedBy = getCurrentUsername();
            userLimitsService.deleteUserLimits(username, vendorId, deletedBy);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to delete user limits for user {} and vendor {}: {}", username, vendorId, e.getMessage(),
                    e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
