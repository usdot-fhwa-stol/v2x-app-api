package usdot.v2x.app.api.models.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Result of a path operation")
public class PathOperationResult {

    @Schema(description = "The path data")
    private PathResponse path;

    @Schema(description = "Whether this was a create operation (true) or update operation (false)")
    private boolean isCreated;

    public PathOperationResult(PathResponse path, boolean isCreated) {
        this.path = path;
        this.isCreated = isCreated;
    }

    public PathOperationResult() {
        // Default constructor for Jackson
    }
}
