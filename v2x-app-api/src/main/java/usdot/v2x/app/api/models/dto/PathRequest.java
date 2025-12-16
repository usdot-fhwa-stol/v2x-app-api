package usdot.v2x.app.api.models.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Schema(description = "Path request for creating or updating paths")
public class PathRequest {

    @NotBlank(message = "Path name is required")
    @Size(max = 255, message = "Path name must not exceed 255 characters")
    @Schema(description = "Path name", example = "Sample path", required = true)
    private String name;

    @Schema(description = "GeoJSON type", example = "Feature")
    private String type = "Feature";

    @Schema(description = "Path properties")
    private PathProperties properties;

    @Schema(description = "Path geometry")
    private PathGeometry geometry;

    @Schema(description = "Timestamps for each coordinate", example = "[0, 100, 200, 300]")
    private List<Long> timestamps;

    @Data
    @Schema(description = "Path properties")
    public static class PathProperties {
        @NotBlank(message = "Properties name is required")
        @Size(max = 255, message = "Properties name must not exceed 255 characters")
        @Schema(description = "Path name in properties", example = "Sample path", required = true)
        private String name;
    }

    @Data
    @Schema(description = "Path geometry")
    public static class PathGeometry {
        @NotBlank(message = "Geometry type is required")
        @Schema(description = "Geometry type", example = "LineString", required = true)
        private String type;

        @NotNull(message = "Coordinates are required")
        @Schema(description = "Coordinate array", example = "[[-105.01621, 39.57422], [-105.01623, 39.57424]]", required = true)
        private List<List<Double>> coordinates;
    }

    public PathRequest(String name, PathProperties properties, PathGeometry geometry, List<Long> timestamps) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Path name cannot be null or empty");
        }
        if (properties == null || properties.getName() == null || properties.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Properties name cannot be null or empty");
        }
        if (geometry == null || geometry.getType() == null || geometry.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("Geometry type cannot be null or empty");
        }
        if (geometry.getCoordinates() == null || geometry.getCoordinates().isEmpty()) {
            throw new IllegalArgumentException("Coordinates cannot be null or empty");
        }
        if (timestamps == null || timestamps.isEmpty()) {
            throw new IllegalArgumentException("Timestamps cannot be null or empty");
        }
        if (geometry.getCoordinates().size() != timestamps.size()) {
            throw new IllegalArgumentException("Number of coordinates must match number of timestamps");
        }

        this.name = name;
        this.properties = properties;
        this.geometry = geometry;
        this.timestamps = timestamps;
    }

    public PathRequest() {
        // Default constructor for Jackson
    }
}
