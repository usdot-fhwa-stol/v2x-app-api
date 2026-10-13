package usdot.v2x.app.api.models.etx.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the ETX {@code POST /api/v1/clients/query} endpoint.
 */
@NoArgsConstructor
@AllArgsConstructor
public @Data class ClientQueryRequest {
    @JsonProperty("VendorId")
    private String vendorId;
}
