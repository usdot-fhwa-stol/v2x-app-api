package usdot.v2x.app.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for geometry operations.
 */
@Configuration
@ConfigurationProperties(prefix = "geometry")
@Getter
@Setter
public class GeometryProperties {
    private double geofenceOffsetMeters;
    /**
     * Extra meters to extend the first and last open (path-based) TIM regions
     * along the path. Width continues to use {@link #geofenceOffsetMeters}.
     * Middle regions are not length-extended. Defaults to 0 so existing
     * deployments keep the previous length behavior.
     */
    private double geofencePathLengthOffsetMeters;
    /**
     * Lateral width in meters at the outer tip of triangular path extensions.
     * {@code 0} produces a pointed triangle; values up to the corridor width
     * produce a trapezoidal tip.
     */
    private double geofencePathExtensionTipWidthMeters;
    private double defaultLaneWidthCm;
}