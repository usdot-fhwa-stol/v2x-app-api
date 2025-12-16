package usdot.v2x.app.api.models.dto;

import usdot.v2x.app.api.models.UserLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

@Data
@Schema(description = "User limits response")
public class UserLimitsResponse {

    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Schema(description = "Username", example = "john.doe")
    private String username;

    @Schema(description = "Vendor ID", example = "NeaeraEval02")
    private String vendorId;

    @Schema(description = "Maximum registrations this specific user can have (personal limit)", example = "10")
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

    public static UserLimitsResponse fromEntity(UserLimits userLimits) {
        UserLimitsResponse response = new UserLimitsResponse();
        response.setId(userLimits.getId());
        response.setUsername(userLimits.getUsername());
        response.setVendorId(userLimits.getVendorId());
        response.setMaxRegistrations(userLimits.getMaxRegistrations());
        response.setIsActive(userLimits.getIsActive());
        response.setCreatedAt(userLimits.getCreatedAt());
        response.setUpdatedAt(userLimits.getUpdatedAt());
        response.setCreatedBy(userLimits.getCreatedBy());
        response.setUpdatedBy(userLimits.getUpdatedBy());
        return response;
    }
}
