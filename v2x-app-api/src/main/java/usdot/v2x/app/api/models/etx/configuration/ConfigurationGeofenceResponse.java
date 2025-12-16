package usdot.v2x.app.api.models.etx.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public @Data class ConfigurationGeofenceResponse extends ConfigurationGeofence {
    @JsonProperty("id")
    private String id;
    @JsonProperty("vendorId")
    private String vendorId;
}