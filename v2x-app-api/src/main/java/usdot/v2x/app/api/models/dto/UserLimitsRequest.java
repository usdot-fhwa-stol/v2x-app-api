package usdot.v2x.app.api.models.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "Request to create or update user limits")
public class UserLimitsRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Username to set limits for", example = "john.doe", required = true)
    private String username;

    @NotBlank(message = "Vendor ID is required")
    @Schema(description = "Vendor ID for the limits", example = "EtxVendor", required = true)
    private String vendorId;

    @NotNull(message = "Max registrations is required")
    @Min(value = 1, message = "Max registrations must be at least 1")
    @Schema(description = "Maximum number of registrations this specific user can have (personal limit)", example = "10", required = true)
    private Integer maxRegistrations;
}
