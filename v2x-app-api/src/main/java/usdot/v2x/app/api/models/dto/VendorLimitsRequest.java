package usdot.v2x.app.api.models.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "Request to create or update vendor limits")
public class VendorLimitsRequest {

    @NotBlank(message = "Vendor ID is required")
    @Schema(description = "Vendor ID for the limits", example = "EtxVendor", required = true)
    private String vendorId;

    @NotNull(message = "Max registrations is required")
    @Min(value = 1, message = "Max registrations must be at least 1")
    @Schema(description = "Maximum number of total registrations this vendor can have across all users", example = "100", required = true)
    private Integer maxRegistrations;
}
