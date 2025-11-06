package usdot.v2x.app.api.etx.registration;

import usdot.v2x.app.api.models.VendorLimits;
import usdot.v2x.app.api.models.dto.VendorLimitsRequest;
import usdot.v2x.app.api.models.dto.VendorLimitsResponse;
import usdot.v2x.app.api.services.VendorLimitsService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/prd/v2/admin/vendor-limits")
@Tag(name = "Vendor Limits Management", description = "Endpoints for managing vendor-wide registration limits")
public class VendorLimitsRestController {

    @Autowired
    private VendorLimitsService vendorLimitsService;

    /**
     * Get all vendor limits
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping
    @Operation(summary = "Get all vendor limits", description = "Retrieves all vendor limits (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor limits retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<VendorLimitsResponse>> getAllVendorLimits() {
        try {
            List<VendorLimits> vendorLimits = vendorLimitsService.getAllVendorLimits();
            List<VendorLimitsResponse> responses = vendorLimits.stream()
                    .map(VendorLimitsResponse::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Failed to get all vendor limits: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get vendor limits by vendor ID
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/{vendorId}")
    @Operation(summary = "Get vendor limits by vendor ID", description = "Retrieves vendor limits for a specific vendor (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor limits retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Vendor limits not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<VendorLimitsResponse> getVendorLimitsByVendorId(
            @Parameter(description = "Vendor ID to get limits for", required = true, example = "EtxVendor") @PathVariable("vendorId") String vendorId) {
        try {
            // For now, we'll return default limits if not found
            // In a real implementation, you might want to return 404
            VendorLimits vendorLimits = vendorLimitsService.getVendorLimits(vendorId, 50); // Default limit
            VendorLimitsResponse response = VendorLimitsResponse.fromEntity(vendorLimits);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get vendor limits for vendor {}: {}", vendorId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create or update vendor limits
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @PostMapping
    @Operation(summary = "Create or update vendor limits", description = "Creates new vendor limits or updates existing ones. "
            +
            "Vendor limits control the total number of registrations a vendor can have across all users (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor limits created/updated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = VendorLimitsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<VendorLimitsResponse> createOrUpdateVendorLimits(
            @Parameter(description = "Vendor limits information", required = true) @Valid @RequestBody VendorLimitsRequest request) {
        try {
            String updatedBy = getCurrentUsername();
            VendorLimits vendorLimits = vendorLimitsService.createOrUpdateVendorLimits(
                    request.getVendorId(),
                    request.getMaxRegistrations(),
                    updatedBy);
            VendorLimitsResponse response = VendorLimitsResponse.fromEntity(vendorLimits);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create/update vendor limits: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete vendor limits
     */
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @DeleteMapping("/{vendorId}")
    @Operation(summary = "Delete vendor limits", description = "Deletes vendor limits for a specific vendor (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor limits deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - invalid or missing authentication"),
            @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Vendor limits not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteVendorLimits(
            @Parameter(description = "Vendor ID to delete limits for", required = true, example = "EtxVendor") @PathVariable("vendorId") String vendorId) {
        try {
            String deletedBy = getCurrentUsername();
            vendorLimitsService.deleteVendorLimits(vendorId, deletedBy);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to delete vendor limits for vendor {}: {}", vendorId, e.getMessage(), e);
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
