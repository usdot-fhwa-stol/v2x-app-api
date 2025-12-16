package usdot.v2x.app.api.models.dto;

import usdot.v2x.app.api.models.Path;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Schema(description = "Path response containing GeoJSON-like structure")
public class PathResponse {

    @Schema(description = "Unique identifier", example = "1")
    private Long id;

    @Schema(description = "Path name", example = "Sample path")
    private String name;

    @Schema(description = "GeoJSON type", example = "Feature")
    private String type;

    @Schema(description = "Path properties")
    private PathProperties properties;

    @Schema(description = "Path geometry")
    private PathGeometry geometry;

    @Schema(description = "Timestamps for each coordinate", example = "[0, 100, 200, 300]")
    private List<Long> timestamps;

    @Schema(description = "Whether the path is active", example = "true")
    private Boolean isActive;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "User who created the path", example = "admin")
    private String createdBy;

    @Schema(description = "User who last updated the path", example = "admin")
    private String updatedBy;

    @Data
    @Schema(description = "Path properties")
    public static class PathProperties {
        @Schema(description = "Path name", example = "Sample path")
        private String name;
    }

    @Data
    @Schema(description = "Path geometry")
    public static class PathGeometry {
        @Schema(description = "Geometry type", example = "LineString")
        private String type;

        @Schema(description = "Coordinate array", example = "[[-105.01621, 39.57422], [-105.01623, 39.57424]]")
        private List<List<Double>> coordinates;
    }

    public static PathResponse fromEntity(Path path) {
        PathResponse response = new PathResponse();
        response.setId(path.getId());
        response.setName(path.getName());
        response.setType(path.getType());
        response.setIsActive(path.getIsActive());
        response.setCreatedAt(path.getCreatedAt());
        response.setUpdatedAt(path.getUpdatedAt());
        response.setCreatedBy(path.getCreatedBy());
        response.setUpdatedBy(path.getUpdatedBy());
        response.setTimestamps(path.getTimestamps());

        // Set properties
        PathProperties properties = new PathProperties();
        properties.setName(path.getName());
        response.setProperties(properties);

        // Set geometry
        PathGeometry geometry = new PathGeometry();
        geometry.setType(path.getGeometryType());

        // Parse coordinates from string list to double list
        if (path.getCoordinates() != null) {
            List<List<Double>> coordList = path.getCoordinates().stream()
                    .map(coordStr -> {
                        String[] parts = coordStr.replace("[", "").replace("]", "").split(",");
                        return List.of(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
                    })
                    .toList();
            geometry.setCoordinates(coordList);
        }
        response.setGeometry(geometry);

        return response;
    }
}
