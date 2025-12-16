package usdot.v2x.app.api.models.dto;

import usdot.v2x.app.api.models.VendorLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "Vendor limits response")
public class VendorLimitsResponse {

    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Schema(description = "Vendor ID", example = "EtxVendor")
    private String vendorId;

    @Schema(description = "Maximum total registrations this vendor can have across all users", example = "100")
    private Integer maxRegistrations;

    @Schema(description = "Whether the limits are active", example = "true")
    private Boolean isActive;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "User who created the limits", example = "admin")
    private String createdBy;

    @Schema(description = "User who last updated the limits", example = "admin")
    private String updatedBy;

    public static VendorLimitsResponse fromEntity(VendorLimits vendorLimits) {
        VendorLimitsResponse response = new VendorLimitsResponse();
        response.setId(vendorLimits.getId());
        response.setVendorId(vendorLimits.getVendorId());
        response.setMaxRegistrations(vendorLimits.getMaxRegistrations());
        response.setIsActive(vendorLimits.getIsActive());
        response.setCreatedAt(vendorLimits.getCreatedAt());
        response.setUpdatedAt(vendorLimits.getUpdatedAt());
        response.setCreatedBy(vendorLimits.getCreatedBy());
        response.setUpdatedBy(vendorLimits.getUpdatedBy());
        return response;
    }
}
