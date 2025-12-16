package usdot.v2x.app.api.models.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Response containing multiple paths in GeoJSON-like format")
public class PathsResponse {

    @Schema(description = "List of paths")
    private List<PathResponse> paths;

    public PathsResponse(List<PathResponse> paths) {
        if (paths == null) {
            throw new IllegalArgumentException("Paths list cannot be null");
        }
        this.paths = paths;
    }

    public PathsResponse() {
        // Default constructor for Jackson
    }
}
