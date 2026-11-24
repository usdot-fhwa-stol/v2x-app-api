package usdot.v2x.app.api.models.dto.provider;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes how the mobile app expands the geohash token (default {@code @}) in
 * provider ACL topic templates.
 * <p>
 * The token occupies one topic level and expands to the geohash characters
 * separated by {@code /}. Publishing uses the event location's geohash at
 * {@code publishPrecision}. Subscribing uses the device's geohash at
 * {@code subscribePrecision} plus every neighbouring cell within
 * {@code subscribeGridRadius} rings (radius 1 is a 3x3 grid), padding with
 * {@code +} up to {@code levels} when the precision is lower than the level count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Geohash topic expansion rules for the '@' token in ACL topic templates")
public class GeohashTopicSpec {

    public static final String DEFAULT_SYMBOL = "@";
    public static final int DEFAULT_LEVELS = 7;
    public static final int DEFAULT_PRECISION = 7;
    public static final int DEFAULT_SUBSCRIBE_GRID_RADIUS = 1;

    @Schema(description = "Token that stands for the '/'-delimited geohash path", example = "@")
    private String symbol = DEFAULT_SYMBOL;

    @Schema(description = "Number of topic levels the geohash path occupies", example = "7")
    private Integer levels = DEFAULT_LEVELS;

    @Schema(description = "Geohash precision used when publishing", example = "7")
    private Integer publishPrecision = DEFAULT_PRECISION;

    @Schema(description = "Geohash precision used when subscribing", example = "7")
    private Integer subscribePrecision = DEFAULT_PRECISION;

    @Schema(description = "Rings of neighbouring cells to subscribe to around the current cell; 1 = 3x3 grid", example = "1")
    private Integer subscribeGridRadius = DEFAULT_SUBSCRIBE_GRID_RADIUS;

    /**
     * @return the number of geohash cells a subscription covers, (2r+1)^2
     */
    public int subscribeCellCount() {
        int side = 2 * subscribeGridRadius + 1;
        return side * side;
    }
}
