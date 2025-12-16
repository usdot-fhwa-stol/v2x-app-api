package usdot.v2x.app.api.models.etx.configuration.geofence;

import com.fasterxml.jackson.annotation.JsonProperty;
import usdot.v2x.app.api.models.etx.configuration.geometry.Geometry;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Schema(description = "GeoJSON Feature representing a single geometric element within a geofence")
public class GeofenceFeature {
    @JsonProperty("type")
    @Schema(description = "GeoJSON type, must be 'Feature'", example = "Feature")
    private String type;

    @JsonProperty("geometry")
    @Schema(description = "GeoJSON geometry object defining the spatial extent")
    private Geometry geometry;

    @JsonProperty("properties")
    @Schema(description = "Properties object is required and must be an empty object. No content is allowed in the properties object. "
            +
            "Must be an empty JSON object: {}. Any properties within this object will cause validation to fail. " +
            "This field enforces that the properties object contains no key-value pairs.", example = "{}", required = true, additionalProperties = AdditionalPropertiesValue.FALSE)
    private Object properties;

    /**
     * Sets the properties object, enforcing that it must be a non-null empty Map.
     * 
     * @param properties The properties object to set. Must be a non-null empty Map.
     * @throws IllegalArgumentException if properties is null or contains any
     *                                  content
     */
    public void setProperties(Object properties) {
        if (properties == null) {
            throw new IllegalArgumentException("Properties object is required and cannot be null.");
        }
        if (properties instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) properties;
            if (!map.isEmpty()) {
                throw new IllegalArgumentException(
                        "Properties object must be empty. Found " + map.size() + " entries.");
            }
        } else {
            throw new IllegalArgumentException(
                    "Properties must be an empty Map. Found: " + properties.getClass().getSimpleName());
        }
        this.properties = properties;
    }
}
