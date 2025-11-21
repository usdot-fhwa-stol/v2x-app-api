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
    private double defaultLaneWidthCm;
}